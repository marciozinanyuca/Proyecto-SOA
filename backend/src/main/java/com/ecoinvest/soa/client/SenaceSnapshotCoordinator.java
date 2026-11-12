package com.ecoinvest.soa.client;

import com.ecoinvest.soa.client.SenaceMapper.ConsultoraMapeada;
import com.ecoinvest.soa.client.SenaceMapper.ProyectoMapeado;
import com.ecoinvest.soa.client.dto.ConsultoraSenaceDto;
import com.ecoinvest.soa.client.dto.ProyectoSenaceDto;
import com.ecoinvest.soa.snapshot.*;
import com.ecoinvest.soa.snapshot.ResultadoLkg.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Sin @Component: registro opt-in mediante SnapshotConfiguration, fuera del aspecto del proveedor. */
public final class SenaceSnapshotCoordinator {
    private final SenaceClient client;
    private final SnapshotRepository repository;
    private final SnapshotValidator validator = new SnapshotValidator();
    private final SenaceMapper mapper = new SenaceMapper();
    private final ObjectMapper json;
    private final Clock clock;

    public SenaceSnapshotCoordinator(SenaceClient proxiedClient, SnapshotRepository repository,
            ObjectMapper json, Clock clock) {
        this.client = Objects.requireNonNull(proxiedClient);
        this.repository = Objects.requireNonNull(repository);
        this.json = Objects.requireNonNull(json).copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.clock = Objects.requireNonNull(clock);
    }

    public ResultadoLkg<List<ProyectoMapeado>> consultarProyectos(String actividad, Integer anio) {
        return consultar(SnapshotKey.proyectos(), client::consultarCarteraCompleta,
                rows -> rows.stream().map(ProyectoMapeado::original).toList(),
                payload -> mapper.mapearProyectos(decode(payload, ProyectoSenaceDto.class), actividad, anio));
    }

    public ResultadoLkg<List<ConsultoraMapeada>> consultarConsultoras() {
        return consultar(SnapshotKey.consultoras(), client::consultarConsultoras,
                rows -> rows.stream().map(ConsultoraMapeada::original).toList(),
                payload -> mapper.mapearConsultoras(decode(payload, ConsultoraSenaceDto.class)));
    }

    private <T> ResultadoLkg<List<T>> consultar(SnapshotKey key, Supplier<ResultadoConsultaSenace<T>> source,
            Function<List<T>, Object> originals, Function<JsonNode, ResultadoConsultaSenace<T>> map) {
        Instant start = clock.instant();
        ResultadoConsultaSenace<T> direct;
        try { direct = source.get(); }
        catch (SenaceClient.ConsultaSenaceException failure) {
            return recover(key, intento(failure.resultado()), map);
        } catch (RuntimeException failure) {
            return recover(key, new IntentoApi("ERROR_EXTERNO", failure instanceof CallNotPermittedException
                    ? "CIRCUITO_ABIERTO" : "ERROR_EXTERNO", null), map);
        }
        IntentoApi attempt = direct == null ? new IntentoApi("ERROR_EXTERNO", "ERROR_EXTERNO", null) : intento(direct);
        if (direct == null || direct.origen() != ResultadoConsultaSenace.Origen.API_DIRECTA_SENACE
                || !Boolean.TRUE.equals(direct.cuerpoCompleto())
                || !(direct.estadoConsulta() == ResultadoConsultaSenace.EstadoConsulta.EXITO
                    || direct.estadoConsulta() == ResultadoConsultaSenace.EstadoConsulta.VACIA)) {
            return recover(key, attempt, map);
        }
        // Snapshot completo ANTES del filtro. Ningun fallo local cambia las metricas del proxy.
        JsonNode payload = json.valueToTree(originals.apply(direct.registros()));
        if (!validator.validPayload(key, payload)) {
            return recover(key, new IntentoApi("FORMATO_INVALIDO", "FORMATO_INVALIDO", true), map);
        }
        SnapshotRepository.WriteStatus saved;
        Instant captured = clock.instant();
        try { saved = repository.save(key, payload, true, start, captured); }
        catch (RuntimeException failure) { saved = SnapshotRepository.WriteStatus.IO_ERROR; }
        ResultadoConsultaSenace<T> mapped = map.apply(payload);
        Respaldo confirmed = saved == SnapshotRepository.WriteStatus.SAVED
                ? new Respaldo(captured, java.time.Duration.ZERO, false, SnapshotEnvelope.SCHEMA_VERSION, "NO_VERIFICADA") : null;
        return new ResultadoLkg<>(mapped.registros(), Origen.API_DIRECTA_SENACE, attempt, confirmed,
                CalidadSenace.de(mapped), null, saved);
    }

    private <T> ResultadoLkg<List<T>> recover(SnapshotKey key, IntentoApi attempt,
            Function<JsonNode, ResultadoConsultaSenace<T>> map) {
        SnapshotRepository.ReadResult read;
        try { read = repository.read(key); }
        catch (RuntimeException failure) { return missing(attempt, SnapshotRepository.ReadStatus.IO_ERROR); }
        if (read == null) return missing(attempt, SnapshotRepository.ReadStatus.IO_ERROR);
        if (read.status() != SnapshotRepository.ReadStatus.AVAILABLE) return missing(attempt, read.status());
        if (read.age() == null || read.age().isNegative() || !validator.valid(read.snapshot(), key, clock.instant())) {
            return missing(attempt, SnapshotRepository.ReadStatus.INVALID);
        }
        try {
            ResultadoConsultaSenace<T> mapped = map.apply(read.snapshot().payload());
            return new ResultadoLkg<>(mapped.registros(), Origen.RESPALDO_LOCAL, attempt,
                    ResultadoLkg.metadata(read), CalidadSenace.de(mapped), read.status(), null);
        } catch (RuntimeException failure) { return missing(attempt, SnapshotRepository.ReadStatus.INVALID); }
    }

    private static <T> ResultadoLkg<List<T>> missing(IntentoApi attempt, SnapshotRepository.ReadStatus status) {
        return new ResultadoLkg<>(null, Origen.SIN_RESPALDO, attempt, null, null, status, null);
    }

    private static IntentoApi intento(ResultadoConsultaSenace<?> direct) {
        return new IntentoApi(direct.estadoConsulta().name(),
                direct.motivo() == null ? null : direct.motivo().name(), direct.cuerpoCompleto());
    }

    private <T> List<T> decode(JsonNode payload, Class<T> type) {
        return json.convertValue(payload, json.getTypeFactory().constructCollectionType(List.class, type));
    }
}
