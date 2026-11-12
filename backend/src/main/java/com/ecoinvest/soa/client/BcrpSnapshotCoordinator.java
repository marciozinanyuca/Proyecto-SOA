package com.ecoinvest.soa.client;

import com.ecoinvest.soa.snapshot.*;
import com.ecoinvest.soa.snapshot.ResultadoLkg.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Coordinador con registro opt-in; no altera transporte, aspectos ni catalogo BCRP. */
public final class BcrpSnapshotCoordinator {
    private final BcrpClient client;
    private final SnapshotRepository repository;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final SnapshotValidator validator = new SnapshotValidator();

    public BcrpSnapshotCoordinator(BcrpClient proxiedClient, SnapshotRepository repository, Clock clock) {
        client = Objects.requireNonNull(proxiedClient);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public ResultadoLkg<Map<String, Object>> consultar(String sector, Integer anio) {
        Map<String, Object> context = client.contextoConsulta(sector, anio);
        if (context.get("motivo") != null) return missing(new IntentoApi("NO_EVALUABLE",
                String.valueOf(context.get("motivo")), null), null);
        SnapshotKey key = SnapshotKey.bcrp((String) context.get("codigoSerie"), (Integer) context.get("anioSolicitado"));
        Instant start = clock.instant();
        Map<String, Object> direct;
        try { direct = client.obtenerDatosBcrp(sector, anio); }
        catch (RuntimeException failure) {
            return recover(key, context, new IntentoApi("ERROR_EXTERNO", failure instanceof CallNotPermittedException
                    ? "CIRCUITO_ABIERTO" : "ERROR_EXTERNO", null));
        }
        if (direct != null && "API_DIRECTA_BCRP".equals(direct.get("origen"))
                && direct.get("valor") instanceof BigDecimal value && direct.get("motivo") == null
                && key.resource().equals(direct.get("codigoSerie"))
                && String.format(java.util.Locale.ROOT, "%04d", key.year()).equals(direct.get("periodo"))) {
            var payload = json.createObjectNode().put("serie", key.resource())
                    .put("periodo", (String) direct.get("periodo")).put("valor", value);
            SnapshotRepository.WriteStatus saved;
            Instant captured = clock.instant();
            try { saved = repository.save(key, payload, true, start, captured); }
            catch (RuntimeException failure) { saved = SnapshotRepository.WriteStatus.IO_ERROR; }
            Respaldo confirmed = saved == SnapshotRepository.WriteStatus.SAVED
                    ? new Respaldo(captured, java.time.Duration.ZERO, false, SnapshotEnvelope.SCHEMA_VERSION, "NO_VERIFICADA") : null;
            // bodyComplete describe finalizacion de lectura de RestTemplate; no cobertura del proveedor.
            return new ResultadoLkg<>(Collections.unmodifiableMap(new LinkedHashMap<>(direct)),
                    Origen.API_DIRECTA_BCRP, new IntentoApi("EXITO", null, true), confirmed, null, null, saved);
        }
        String reason = direct == null ? "ERROR_EXTERNO" : safeReason(direct.get("motivo"));
        IntentoApi attempt = new IntentoApi(reason.equals("SIN_OBSERVACION") || reason.equals("PERIODO_FUERA_DE_RANGO")
                ? "SIN_OBSERVACION" : "ERROR_EXTERNO", reason, null);
        // Ausencia economica declarada no es una caida: no ocultarla con un valor historico.
        if (attempt.estado().equals("SIN_OBSERVACION")) return missing(attempt, null);
        return recover(key, context, attempt);
    }

    /** Contexto economico sin valores historicos ni URLs, incluso cuando no hay respaldo. */
    public Map<String, Object> contextoConsulta(String sector, Integer anio) {
        Map<String, Object> context = new LinkedHashMap<>(client.contextoConsulta(sector, anio));
        context.remove("urlFuente");
        return Collections.unmodifiableMap(context);
    }

    private ResultadoLkg<Map<String, Object>> recover(SnapshotKey key, Map<String, Object> context, IntentoApi attempt) {
        SnapshotRepository.ReadResult read;
        try { read = repository.read(key); }
        catch (RuntimeException failure) { return missing(attempt, SnapshotRepository.ReadStatus.IO_ERROR); }
        if (read == null) return missing(attempt, SnapshotRepository.ReadStatus.IO_ERROR);
        if (read.status() != SnapshotRepository.ReadStatus.AVAILABLE) return missing(attempt, read.status());
        if (read.age() == null || read.age().isNegative() || !validator.valid(read.snapshot(), key, clock.instant())) {
            return missing(attempt, SnapshotRepository.ReadStatus.INVALID);
        }
        BigDecimal value = read.snapshot().payload().get("valor").decimalValue();
        Map<String, Object> data = new LinkedHashMap<>(context);
        data.remove("urlFuente");
        data.remove("motivo");
        data.put("origen", "RESPALDO_LOCAL");
        data.put("valor", value);
        data.put("variacionSeleccionada", value);
        data.put("periodo", String.format(java.util.Locale.ROOT, "%04d", key.year()));
        data.put("periods", List.of(Map.of("name", data.get("periodo"), "values", List.of(value.toString()))));
        return new ResultadoLkg<>(Collections.unmodifiableMap(data), Origen.RESPALDO_LOCAL, attempt,
                ResultadoLkg.metadata(read), null, read.status(), null);
    }

    private static String safeReason(Object reason) {
        if (reason instanceof String text && List.of("SIN_OBSERVACION", "PERIODO_FUERA_DE_RANGO",
                "FORMATO_INVALIDO", "ERROR_EXTERNO", "CIRCUITO_ABIERTO").contains(text)) return text;
        return "ERROR_EXTERNO";
    }

    private static ResultadoLkg<Map<String, Object>> missing(IntentoApi attempt, SnapshotRepository.ReadStatus status) {
        return new ResultadoLkg<>(null, Origen.SIN_RESPALDO, attempt, null, null, status, null);
    }
}
