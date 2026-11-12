package com.ecoinvest.soa.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

/** Conserva solo metadatos de operaciones terminadas. La observacion no hace I/O adicional. */
public final class ObservedSnapshotRepository implements SnapshotRepository {
    private final SnapshotRepository delegate;
    private final SnapshotStatusService status;
    public ObservedSnapshotRepository(SnapshotRepository delegate, SnapshotStatusService status) {
        this.delegate = Objects.requireNonNull(delegate);
        this.status = Objects.requireNonNull(status);
    }
    @Override public ReadResult read(SnapshotKey key) {
        ReadResult result;
        try { result = delegate.read(key); }
        catch (RuntimeException failure) { result = new ReadResult(ReadStatus.IO_ERROR, null, null, false); }
        if (result == null) result = new ReadResult(ReadStatus.IO_ERROR, null, null, false);
        status.read(key, result);
        return result;
    }
    @Override public WriteStatus save(SnapshotKey key, JsonNode payload, boolean complete, Instant start, Instant captured) {
        WriteStatus result;
        try { result = delegate.save(key, payload, complete, start, captured); }
        catch (RuntimeException failure) { result = WriteStatus.IO_ERROR; }
        if (result == null) result = WriteStatus.IO_ERROR;
        status.write(key, result, captured);
        return result;
    }
    @Override public void close() { delegate.close(); }
}
