package com.ecoinvest.soa.snapshot;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Un propietario por directorio, cierre explicito y publicacion atomica sin degradacion silenciosa.
 * Requiere directorio/ancestros bajo control del operador: no es aislamiento frente a un actor
 * con permiso para sustituirlos durante una operacion. No garantiza durabilidad ante corte electrico
 * ni persistencia del filesystem de alojamiento. No abre directorios hasta read/save.
 */
public class JsonFileSnapshotRepository implements SnapshotRepository {
    private final SnapshotProperties properties;
    private final SnapshotValidator validator;
    private final Clock clock;
    private final ObjectMapper mapper;
    private FileChannel ownershipChannel;
    private FileLock ownership;
    private boolean closed;

    public JsonFileSnapshotRepository(SnapshotProperties properties, SnapshotValidator validator, Clock clock) {
        this.properties = java.util.Objects.requireNonNull(properties);
        this.validator = java.util.Objects.requireNonNull(validator);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.mapper.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
    }

    @Override
    public synchronized ReadResult read(SnapshotKey key) {
        java.util.Objects.requireNonNull(key);
        if (closed) return readFailure(ReadStatus.IO_ERROR);
        try {
            checkAncestors(properties.directory());
            if (!Files.exists(properties.directory(), LinkOption.NOFOLLOW_LINKS)) return readFailure(ReadStatus.MISSING);
            if (!acquireOwnership()) return readFailure(ReadStatus.OWNERSHIP_UNAVAILABLE);
            return readLocked(key);
        } catch (IOException | SecurityException exception) { return readFailure(ReadStatus.IO_ERROR); }
    }

    @Override
    public synchronized WriteStatus save(SnapshotKey key, JsonNode payload, boolean bodyComplete,
            Instant requestStartedAt, Instant capturedAt) {
        if (closed) return WriteStatus.IO_ERROR;
        if (key == null || !bodyComplete || !validator.validPayload(key, payload)
                || requestStartedAt == null || capturedAt == null
                || requestStartedAt.isAfter(capturedAt) || capturedAt.isAfter(clock.instant())) {
            return WriteStatus.INVALID;
        }
        SnapshotEnvelope unsigned = new SnapshotEnvelope(SnapshotEnvelope.SCHEMA_VERSION, key,
                requestStartedAt, capturedAt, true, "NO_VERIFICADA", "VALIDADO",
                validator.count(key, payload), payload, null);
        SnapshotEnvelope envelope = new SnapshotEnvelope(unsigned.schemaVersion(), key, requestStartedAt,
                capturedAt, true, unsigned.coverage(), unsigned.validation(), unsigned.recordCount(),
                unsigned.payload(), validator.checksum(unsigned));
        Path temporary = null;
        try {
            createDirectoriesChecked(properties.directory());
            if (!acquireOwnership()) return WriteStatus.OWNERSHIP_UNAVAILABLE;
            ReadResult previous = readLocked(key);
            if (previous.status() == ReadStatus.IO_ERROR) return WriteStatus.IO_ERROR;
            if (previous.snapshot() != null) {
                if (!requestStartedAt.isAfter(previous.snapshot().requestStartedAt())) {
                    return WriteStatus.OLDER_RESPONSE_REJECTED;
                }
                if (envelope.recordCount() == 0 && previous.snapshot().recordCount() > 0) {
                    return WriteStatus.EMPTY_REPLACEMENT_REJECTED;
                }
            }
            Path target = target(key);
            createDirectoriesChecked(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".snapshot-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                // Limite antes de escribir cada bloque: no serializa un archivo ilimitado primero.
                OutputStream bounded = new OutputStream() {
                    private long written;
                    @Override public void write(int value) throws IOException { write(new byte[] {(byte) value}, 0, 1); }
                    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                        if (length > properties.maxFileBytes() - written) throw new IOException("LIMITE_SNAPSHOT");
                        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length);
                        while (buffer.hasRemaining()) channel.write(buffer);
                        written += length;
                    }
                };
                mapper.writeValue(bounded, envelope);
                channel.force(true);
            }
            // Verificacion antes de publicacion; no usa la fecha del archivo como fecha de captura.
            SnapshotEnvelope written = load(temporary);
            if (!validator.valid(written, key, clock.instant())) return WriteStatus.INVALID;
            checkAncestors(target);
            publish(temporary, target);
            temporary = null;
            return WriteStatus.SAVED;
        } catch (AtomicMoveNotSupportedException exception) {
            return WriteStatus.ATOMIC_MOVE_UNSUPPORTED;
        } catch (IOException | SecurityException exception) {
            return WriteStatus.IO_ERROR;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException | SecurityException ignored) {
                    // Temporal propio huerfano no se interpreta como respaldo ni se publica.
                }
            }
        }
    }

    /** Punto de prueba de fallo de publicacion, sin provocar errores sobre datos reales. */
    protected void publish(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private ReadResult readLocked(SnapshotKey key) {
        try {
            Path path = target(key);
            checkAncestors(path);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return readFailure(ReadStatus.MISSING);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return readFailure(ReadStatus.INVALID);
            SnapshotEnvelope envelope;
            try { envelope = load(path); }
            catch (com.fasterxml.jackson.core.JacksonException exception) { return readFailure(ReadStatus.INVALID); }
            if (!validator.valid(envelope, key, clock.instant())) return readFailure(ReadStatus.INVALID);
            Duration age = Duration.between(envelope.capturedAt(), clock.instant());
            return new ReadResult(age.compareTo(properties.maxAge(key)) > 0 ? ReadStatus.EXPIRED : ReadStatus.AVAILABLE,
                    envelope, age, age.compareTo(properties.staleAfter(key)) > 0);
        } catch (IOException | SecurityException exception) { return readFailure(ReadStatus.IO_ERROR); }
    }

    private SnapshotEnvelope load(Path path) throws IOException {
        try (var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                InputStream input = Channels.newInputStream(channel)) {
            if (channel.size() > properties.maxFileBytes()) throw new IOException("LIMITE_SNAPSHOT");
            byte[] bytes = input.readNBytes((int) properties.maxFileBytes() + 1);
            if (bytes.length > properties.maxFileBytes()) throw new IOException("LIMITE_SNAPSHOT");
            return mapper.readValue(bytes, SnapshotEnvelope.class);
        }
    }

    private boolean acquireOwnership() throws IOException {
        if (ownership != null && ownership.isValid()) return true;
        Path lockPath = properties.directory().resolve(".owner.lock");
        checkAncestors(lockPath);
        FileChannel candidate = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock lock = candidate.tryLock();
            if (lock == null) { candidate.close(); return false; }
            ownershipChannel = candidate;
            ownership = lock;
            return true;
        } catch (OverlappingFileLockException exception) {
            candidate.close();
            return false;
        } catch (IOException | RuntimeException exception) {
            candidate.close();
            throw exception;
        }
    }

    private Path target(SnapshotKey key) throws IOException {
        Path path = properties.directory().resolve(key.relativePath()).normalize();
        if (!path.startsWith(properties.directory())) throw new IOException("DESTINO_INVALIDO");
        return path;
    }

    private static void checkAncestors(Path path) throws IOException {
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("ENLACE_NO_PERMITIDO");
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && Files.readAttributes(cursor, java.nio.file.attribute.BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS).isOther()) throw new IOException("ARCHIVO_ESPECIAL_NO_PERMITIDO");
        }
    }

    private static void createDirectoriesChecked(Path path) throws IOException {
        checkAncestors(path);
        Files.createDirectories(path);
        checkAncestors(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("DIRECTORIO_INVALIDO");
    }

    private static ReadResult readFailure(ReadStatus status) { return new ReadResult(status, null, null, false); }

    @Override public synchronized void close() {
        closed = true;
        if (ownership != null) try { ownership.release(); } catch (IOException ignored) { }
        if (ownershipChannel != null) try { ownershipChannel.close(); } catch (IOException ignored) { }
    }
}
