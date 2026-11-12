package com.ecoinvest.soa.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;

/** No realiza HTTP. La evidencia de cuerpo completo la aporta el futuro coordinador. */
public interface SnapshotRepository extends AutoCloseable {
    enum ReadStatus { AVAILABLE, MISSING, EXPIRED, INVALID, IO_ERROR, OWNERSHIP_UNAVAILABLE }
    enum WriteStatus { SAVED, INVALID, EMPTY_REPLACEMENT_REJECTED, OLDER_RESPONSE_REJECTED,
        IO_ERROR, ATOMIC_MOVE_UNSUPPORTED, OWNERSHIP_UNAVAILABLE }
    record ReadResult(ReadStatus status, SnapshotEnvelope snapshot, Duration age, boolean stale) { }

    ReadResult read(SnapshotKey key);
    WriteStatus save(SnapshotKey key, JsonNode payload, boolean bodyComplete,
            Instant requestStartedAt, Instant capturedAt);
    @Override void close();
}
