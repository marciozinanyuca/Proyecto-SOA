package com.ecoinvest.soa.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Payload oficial permitido, nunca URL, cabeceras, credencial o cuerpo de error. */
public record SnapshotEnvelope(int schemaVersion, SnapshotKey key, Instant requestStartedAt,
        Instant capturedAt, boolean bodyComplete, String coverage, String validation,
        int recordCount, JsonNode payload, String checksum) {
    public static final int SCHEMA_VERSION = 1;

    public SnapshotEnvelope {
        payload = payload == null ? null : payload.deepCopy();
    }

    @Override public JsonNode payload() { return payload == null ? null : payload.deepCopy(); }
    @Override public String toString() { return "SnapshotEnvelope[contenido omitido]"; }
}
