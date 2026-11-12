package com.ecoinvest.soa.snapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Observaciones acotadas en memoria. No escanea disco, hace HTTP ni acredita integridad actual. */
public final class SnapshotStatusService {
    private static final int MAX_ENTRIES = 128;
    private record Observation(String existence, String integrity, Instant captured, Instant verified,
            Instant observed, String read, String write, boolean failure) { }
    private final Map<SnapshotKey, Observation> observations = new LinkedHashMap<>();
    private final SnapshotProperties properties;
    private final Clock clock;

    public SnapshotStatusService(SnapshotProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void read(SnapshotKey key, SnapshotRepository.ReadResult result) {
        Instant now = clock.instant();
        Observation old = observations.get(key);
        boolean valid = (result.status() == SnapshotRepository.ReadStatus.AVAILABLE
                || result.status() == SnapshotRepository.ReadStatus.EXPIRED) && result.snapshot() != null;
        String existence = valid ? "EXISTE" : result.status() == SnapshotRepository.ReadStatus.MISSING
                ? "NO_EXISTE" : "NO_VERIFICADA";
        String integrity = valid ? "VALIDADA" : result.status() == SnapshotRepository.ReadStatus.INVALID
                ? "INVALIDA" : "NO_VERIFICADA";
        if (valid && old != null && old.captured() != null && old.captured().isAfter(result.snapshot().capturedAt())) return;
        put(key, new Observation(existence, integrity,
                valid ? result.snapshot().capturedAt() : old == null ? null : old.captured(),
                valid ? now : null, now, result.status().name(), old == null ? null : old.write(),
                isFailure(result.status().name())));
    }

    public synchronized void write(SnapshotKey key, SnapshotRepository.WriteStatus result, Instant captured) {
        Instant now = clock.instant();
        Observation old = observations.get(key);
        boolean saved = result == SnapshotRepository.WriteStatus.SAVED;
        if (saved && old != null && old.captured() != null && old.captured().isAfter(captured)) return;
        put(key, new Observation(saved ? "EXISTE" : old == null ? "NO_VERIFICADA" : old.existence(),
                saved ? "VALIDADA" : old == null ? "NO_VERIFICADA" : old.integrity(),
                saved ? captured : old == null ? null : old.captured(),
                saved ? now : old == null ? null : old.verified(), now,
                old == null ? null : old.read(), result.name(), isFailure(result.name())));
    }

    private void put(SnapshotKey key, Observation value) {
        observations.remove(key);
        observations.put(key, value);
        if (observations.size() > MAX_ENTRIES) observations.remove(observations.keySet().iterator().next());
    }

    public synchronized String state(SnapshotKey.Source source) {
        boolean operational = false;
        for (var entry : observations.entrySet()) {
            if (source != null && entry.getKey().source() != source) continue;
            Observation value = entry.getValue();
            // La operacion mas reciente determina la incidencia; una captura previa no la oculta.
            if ("INVALIDA".equals(value.integrity()) || value.failure()) {
                return "CON_INCIDENCIAS";
            }
            operational |= value.verified() != null;
        }
        return operational ? "OPERATIVO" : "HABILITADO_SIN_VERIFICACION";
    }

    private static boolean isFailure(String status) {
        return status != null && List.of("IO_ERROR", "OWNERSHIP_UNAVAILABLE", "INVALID",
                "ATOMIC_MOVE_UNSUPPORTED").contains(status);
    }

    public synchronized Map<String, Object> snapshot() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (var entry : observations.entrySet()) {
            SnapshotKey key = entry.getKey();
            Observation value = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fuente", key.source().name());
            row.put("recurso", key.resource());
            if (key.year() != null) row.put("periodo", String.format(java.util.Locale.ROOT, "%04d", key.year()));
            row.put("existencia", value.existence());
            row.put("integridad", value.integrity());
            row.put("observadoEn", value.observed().toString());
            if (value.verified() != null) row.put("verificadoEn", value.verified().toString());
            if (value.captured() != null) {
                row.put("ultimaCapturaValida", value.captured().toString());
                Duration age = Duration.between(value.captured(), clock.instant());
                if (!age.isNegative()) {
                    row.put("edadSegundos", age.getSeconds());
                    row.put("antiguo", age.compareTo(properties.staleAfter(key)) > 0);
                    row.put("vencido", age.compareTo(properties.maxAge(key)) > 0);
                }
            }
            if (value.read() != null) row.put("lectura", value.read());
            if (value.write() != null) row.put("escritura", value.write());
            records.add(row);
        }
        return Map.of("estado", state(null), "alcance", "ULTIMAS_OBSERVACIONES_DEL_PROCESO",
                "limiteObservaciones", MAX_ENTRIES, "observaciones", records);
    }
}
