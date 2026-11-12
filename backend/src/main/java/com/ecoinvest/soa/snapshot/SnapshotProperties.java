package com.ecoinvest.soa.snapshot;

import java.nio.file.Path;
import java.time.Duration;

/** Las edades son decisiones explicitas de despliegue: no hay TTL economico implicito. */
public record SnapshotProperties(Path directory, long maxFileBytes, Duration senaceStaleAfter,
        Duration senaceMaxAge, Duration bcrpStaleAfter, Duration bcrpMaxAge) {
    public SnapshotProperties {
        if (directory == null || maxFileBytes < 1024 || maxFileBytes > 64L * 1024 * 1024) {
            throw new IllegalArgumentException("CONFIGURACION_SNAPSHOT_INVALIDA");
        }
        validateAge(senaceStaleAfter, senaceMaxAge);
        validateAge(bcrpStaleAfter, bcrpMaxAge);
        directory = directory.toAbsolutePath().normalize();
    }

    private static void validateAge(Duration stale, Duration max) {
        if (stale == null || max == null || stale.isNegative() || max.isNegative()
                || max.isZero() || stale.compareTo(max) > 0) {
            throw new IllegalArgumentException("POLITICA_ANTIGUEDAD_INVALIDA");
        }
    }

    Duration maxAge(SnapshotKey key) { return key.source() == SnapshotKey.Source.SENACE ? senaceMaxAge : bcrpMaxAge; }
    Duration staleAfter(SnapshotKey key) { return key.source() == SnapshotKey.Source.SENACE ? senaceStaleAfter : bcrpStaleAfter; }
}
