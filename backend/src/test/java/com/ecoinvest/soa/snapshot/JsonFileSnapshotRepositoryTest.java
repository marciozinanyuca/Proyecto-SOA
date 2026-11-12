package com.ecoinvest.soa.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.ecoinvest.soa.snapshot.SnapshotRepository.*;

/** Solo temporales JUnit y datos artificiales. Fallos de disco inyectados se identifican como tales. */
class JsonFileSnapshotRepositoryTest {
    @TempDir Path temporary;
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private SnapshotProperties properties() {
        // Politica de prueba artificial: no constituye aprobacion de TTL productivo.
        return new SnapshotProperties(temporary.resolve("store"), 16384,
                Duration.ofHours(1), Duration.ofDays(2), Duration.ofDays(1), Duration.ofDays(10));
    }

    private JsonFileSnapshotRepository repository(Instant time) {
        return new JsonFileSnapshotRepository(properties(), new SnapshotValidator(), Clock.fixed(time, ZoneOffset.UTC));
    }

    private WriteStatus save(SnapshotRepository repository, long id, Instant capture) throws Exception {
        return repository.save(SnapshotKey.proyectos(), mapper.readTree("[{\"ID\":" + id
                + ",\"RUC\":\"00000000001\",\"LONGITUD\":-77.1234567890123456789,\"LATITUD\":-12.1}]"),
                true, capture.minusSeconds(1), capture);
    }

    @Test void constructorAndMissingReadDoNotCreateDirectory() {
        try (var repository = repository(now)) {
            assertEquals(ReadStatus.MISSING, repository.read(SnapshotKey.proyectos()).status());
            assertFalse(Files.exists(properties().directory()));
        }
    }

    @Test void dataSurvivesRepositoryReconstructionWithPrecisionAndNoFiltering() throws Exception {
        try (var repository = repository(now)) { assertEquals(WriteStatus.SAVED, save(repository, 9007199254740993L, now)); }
        try (var restarted = repository(now.plusSeconds(60))) {
            var result = restarted.read(SnapshotKey.proyectos());
            assertEquals(ReadStatus.AVAILABLE, result.status());
            assertEquals(Duration.ofSeconds(60), result.age());
            assertEquals(9007199254740993L, result.snapshot().payload().get(0).path("ID").longValue());
            assertEquals("00000000001", result.snapshot().payload().get(0).path("RUC").textValue());
            assertEquals(new java.math.BigDecimal("-77.1234567890123456789"),
                    result.snapshot().payload().get(0).path("LONGITUD").decimalValue());
            assertEquals(ReadStatus.MISSING, restarted.read(SnapshotKey.consultoras()).status());
        }
    }

    @Test void invalidAndPartialUpdatesNeverReplaceGoodSnapshot() throws Exception {
        try (var repository = repository(now)) {
            assertEquals(WriteStatus.SAVED, save(repository, 1, now.minusSeconds(10)));
            assertEquals(WriteStatus.INVALID, repository.save(SnapshotKey.proyectos(), mapper.readTree("[{\"ID\":2}]"), false, now, now));
            assertEquals(WriteStatus.INVALID, repository.save(SnapshotKey.proyectos(), mapper.readTree("{}"), true, now, now));
            assertEquals(WriteStatus.EMPTY_REPLACEMENT_REJECTED, repository.save(SnapshotKey.proyectos(), mapper.readTree("[]"), true, now, now));
            assertEquals(1, repository.read(SnapshotKey.proyectos()).snapshot().payload().get(0).path("ID").intValue());
        }
    }

    @Test void initialEmptySnapshotIsExplicitlyRetained() throws Exception {
        try (var repository = repository(now)) {
            assertEquals(WriteStatus.SAVED, repository.save(SnapshotKey.consultoras(), mapper.readTree("[]"), true, now, now));
            assertEquals(0, repository.read(SnapshotKey.consultoras()).snapshot().recordCount());
        }
    }

    @Test void consultantOrderDuplicatesAndOriginalDatesSurviveStorage() throws Exception {
        try (var repository = repository(now)) {
            var payload = mapper.readTree("[{\"RAZON_SOCIAL\":\"PRUEBA\",\"FECHA_RD\":\"texto original\"},"
                    + "{\"RAZON_SOCIAL\":\"PRUEBA\",\"FECHA_RD\":\"texto original\"}]");
            assertEquals(WriteStatus.SAVED, repository.save(SnapshotKey.consultoras(), payload, true, now, now));
            var read = repository.read(SnapshotKey.consultoras()).snapshot();
            assertEquals(2, read.recordCount());
            assertEquals(payload, read.payload());
        }
    }

    @Test void expiryUsesCaptureTimeAndDoesNotDeleteFile() throws Exception {
        try (var repository = repository(now)) { assertEquals(WriteStatus.SAVED, save(repository, 1, now)); }
        try (var stale = repository(now.plusSeconds(7200))) {
            assertTrue(stale.read(SnapshotKey.proyectos()).stale());
            assertEquals(ReadStatus.AVAILABLE, stale.read(SnapshotKey.proyectos()).status());
        }
        try (var expired = repository(now.plus(Duration.ofDays(3)))) {
            assertEquals(ReadStatus.EXPIRED, expired.read(SnapshotKey.proyectos()).status());
            assertTrue(Files.exists(properties().directory().resolve(SnapshotKey.proyectos().relativePath())));
        }
    }

    @Test void corruptionSchemaAndDuplicateJsonFieldsAreRejectedOnRead() throws Exception {
        try (var repository = repository(now)) {
            assertEquals(WriteStatus.SAVED, save(repository, 1, now));
            Path file = properties().directory().resolve(SnapshotKey.proyectos().relativePath());
            String original = Files.readString(file);
            Files.writeString(file, original.replace("\"ID\":1", "\"ID\":2"));
            assertEquals(ReadStatus.INVALID, repository.read(SnapshotKey.proyectos()).status());
            Files.writeString(file, original.replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
            assertEquals(ReadStatus.INVALID, repository.read(SnapshotKey.proyectos()).status());
            Files.writeString(file, "{\"schemaVersion\":1,\"schemaVersion\":2}");
            assertEquals(ReadStatus.INVALID, repository.read(SnapshotKey.proyectos()).status());
        }
    }

    @Test void economicPeriodNeverUsesNeighboringSnapshot() throws Exception {
        try (var repository = repository(now)) {
            assertEquals(WriteStatus.SAVED, repository.save(SnapshotKey.bcrp("PM04973AA", 2017),
                    mapper.readTree("{\"serie\":\"PM04973AA\",\"periodo\":\"2017\",\"valor\":4.25}"), true, now, now));
            assertEquals(ReadStatus.MISSING, repository.read(SnapshotKey.bcrp("PM04973AA", 2018)).status());
            assertEquals(ReadStatus.MISSING, repository.read(SnapshotKey.bcrp("PM04974AA", 2017)).status());
        }
    }

    @Test void simulatedPublishFailurePreservesPreviousFileAndCleansOwnTemporary() throws Exception {
        try (var repository = repository(now)) { assertEquals(WriteStatus.SAVED, save(repository, 1, now.minusSeconds(10))); }
        try (var failure = new JsonFileSnapshotRepository(properties(), new SnapshotValidator(), Clock.fixed(now, ZoneOffset.UTC)) {
            @Override protected void publish(Path source, Path target) throws IOException { throw new IOException("FALLO_INYECTADO"); }
        }) {
            assertEquals(WriteStatus.IO_ERROR, save(failure, 2, now));
            assertEquals(1, failure.read(SnapshotKey.proyectos()).snapshot().payload().get(0).path("ID").intValue());
            try (var files = Files.list(properties().directory().resolve("senace"))) {
                assertEquals(1, files.count());
            }
        }
    }

    @Test void unsupportedAtomicMoveDoesNotFallBackToNonAtomicReplacement() throws Exception {
        try (var repository = new JsonFileSnapshotRepository(properties(), new SnapshotValidator(), Clock.fixed(now, ZoneOffset.UTC)) {
            @Override protected void publish(Path source, Path target) throws IOException {
                throw new AtomicMoveNotSupportedException("sintetico", "sintetico", "inyectado");
            }
        }) {
            assertEquals(WriteStatus.ATOMIC_MOVE_UNSUPPORTED, save(repository, 1, now));
            assertEquals(ReadStatus.MISSING, repository.read(SnapshotKey.proyectos()).status());
        }
    }

    @Test void singleOwnerLockAndClosedRepositoryAreEnforced() throws Exception {
        try (var first = repository(now); var second = repository(now)) {
            assertEquals(WriteStatus.SAVED, save(first, 1, now));
            assertEquals(ReadStatus.OWNERSHIP_UNAVAILABLE, second.read(SnapshotKey.proyectos()).status());
            assertEquals(WriteStatus.OWNERSHIP_UNAVAILABLE, save(second, 2, now));
            first.close();
            assertEquals(ReadStatus.AVAILABLE, second.read(SnapshotKey.proyectos()).status());
            assertEquals(WriteStatus.IO_ERROR, save(first, 3, now));
        }
    }

    @Test void concurrentResponsesRetainNewestRequestRegardlessOfCompletionOrder() throws Exception {
        try (var repository = repository(now)) {
            var executor = Executors.newFixedThreadPool(2);
            try {
                var newer = executor.submit(() -> save(repository, 2, now));
                var older = executor.submit(() -> save(repository, 1, now.minusSeconds(10)));
                assertEquals(WriteStatus.SAVED, newer.get());
                assertTrue(java.util.Set.of(WriteStatus.SAVED, WriteStatus.OLDER_RESPONSE_REJECTED).contains(older.get()));
                assertEquals(2, repository.read(SnapshotKey.proyectos()).snapshot().payload().get(0).path("ID").intValue());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
            }
        }
    }

    @Test void oversizedUpdatePreservesExistingSnapshot() throws Exception {
        try (var repository = repository(now)) {
            assertEquals(WriteStatus.SAVED, save(repository, 1, now.minusSeconds(10)));
            var payload = mapper.createArrayNode();
            payload.addObject().put("ID", 2).put("DESCRIPCION", "x".repeat(20000));
            assertEquals(WriteStatus.IO_ERROR, repository.save(SnapshotKey.proyectos(), payload, true, now, now));
            assertEquals(1, repository.read(SnapshotKey.proyectos()).snapshot().payload().get(0).path("ID").intValue());
        }
    }

    @Test void orphanTemporaryIsNotInterpretedAsSnapshot() throws Exception {
        Files.createDirectories(properties().directory().resolve("senace"));
        Files.writeString(properties().directory().resolve("senace/.snapshot-orphan.tmp"), "incompleto");
        try (var repository = repository(now)) {
            assertEquals(ReadStatus.MISSING, repository.read(SnapshotKey.proyectos()).status());
        }
    }
}
