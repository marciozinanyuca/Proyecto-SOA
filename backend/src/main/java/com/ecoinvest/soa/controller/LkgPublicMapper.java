package com.ecoinvest.soa.controller;

import com.ecoinvest.model.*;
import com.ecoinvest.soa.client.ResultadoConsultaSenace;
import com.ecoinvest.soa.snapshot.ResultadoLkg;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** Traduccion explicita al contrato generado. No hace HTTP ni consulta el repositorio. */
public final class LkgPublicMapper {
    public static final String CAPTURE = "X-Senace-Respaldo-Capturado-En";
    public static final String AGE = "X-Senace-Respaldo-Edad-Segundos";
    public static final String STALE = "X-Senace-Respaldo-Antiguo";
    public static final String ATTEMPT = "X-Senace-Intento-Estado";
    public static final String REASON = "X-Senace-Intento-Motivo";
    public static final String READ = "X-Senace-Snapshot-Lectura";
    public static final String WRITE = "X-Senace-Snapshot-Escritura";
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public boolean availableSenace(ResultadoLkg<?> result) {
        return result != null && result.datos() != null && result.calidadSenace() != null
                && (result.origen() == ResultadoLkg.Origen.API_DIRECTA_SENACE
                    || result.origen() == ResultadoLkg.Origen.RESPALDO_LOCAL)
                && (result.calidadSenace().estadoDatos() == ResultadoConsultaSenace.EstadoConsulta.EXITO
                    || result.calidadSenace().estadoDatos() == ResultadoConsultaSenace.EstadoConsulta.VACIA);
    }

    public ConsultaSenace senace(ResultadoLkg<?> result) {
        ConsultaSenace out = new ConsultaSenace();
        boolean available = availableSenace(result);
        out.setOrigen(available ? OrigenSenace.fromValue(result.origen().name()) : OrigenSenace.SIN_RESPALDO);
        out.setCobertura(CoberturaSenace.NO_VERIFICADA);
        out.setCuerpoCompleto(available ? Boolean.TRUE : result.intentoApi().cuerpoCompleto());
        if (available) {
            var quality = result.calidadSenace();
            out.setEstado(EstadoConsultaSenace.fromValue(quality.estadoDatos().name()));
            out.setEstadoFiltrado(EstadoFiltradoSenace.fromValue(quality.filtrado().name()));
            out.setConteos(json.convertValue(quality.conteos(), ConteosSenace.class));
            out.setAdvertencias(quality.advertencias().stream()
                    .map(value -> AdvertenciaSenace.fromValue(value.name())).toList());
            // Motivos de filtrado describen los datos, no el fallo del proveedor actual.
            if (quality.estadoDatos() == ResultadoConsultaSenace.EstadoConsulta.VACIA) {
                out.setMotivo(MotivoSenace.RESPUESTA_OFICIAL_VACIA);
            } else if (quality.filtrado() == ResultadoConsultaSenace.EstadoFiltrado.NO_EVALUABLE) {
                out.setMotivo(MotivoSenace.FECHAS_NO_EVALUABLES);
            } else if (quality.filtrado() == ResultadoConsultaSenace.EstadoFiltrado.SIN_COINCIDENCIAS) {
                out.setMotivo(MotivoSenace.SIN_COINCIDENCIAS);
            }
        } else {
            String state = result.intentoApi().estado();
            out.setEstado("FORMATO_INVALIDO".equals(state) ? EstadoConsultaSenace.FORMATO_INVALIDO
                    : "PARCIAL".equals(state) ? EstadoConsultaSenace.PARCIAL : EstadoConsultaSenace.ERROR_EXTERNO);
            out.setEstadoFiltrado(EstadoFiltradoSenace.NO_APLICABLE);
            out.setConteos(new ConteosSenace());
            out.setAdvertencias(List.of());
            String reason = safeReason(result.intentoApi().motivo());
            out.setMotivo(reason != null && Arrays.stream(ResultadoConsultaSenace.Motivo.values())
                    .anyMatch(value -> value.name().equals(reason))
                    ? MotivoSenace.fromValue(reason) : MotivoSenace.ERROR_EXTERNO);
        }
        out.setLkg(metadata(result));
        return out;
    }

    public LkgMetadata metadata(ResultadoLkg<?> result) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> attempt = new LinkedHashMap<>();
        String state = result.intentoApi().estado();
        attempt.put("estado", List.of("EXITO", "VACIA", "ERROR_EXTERNO", "FORMATO_INVALIDO", "PARCIAL",
                "NO_EVALUABLE", "SIN_OBSERVACION").contains(state) ? state : "ERROR_EXTERNO");
        String reason = safeReason(result.intentoApi().motivo());
        if (reason != null) attempt.put("motivo", reason);
        if (result.intentoApi().cuerpoCompleto() != null) attempt.put("cuerpoCompleto", result.intentoApi().cuerpoCompleto());
        values.put("intentoApi", attempt);
        if (result.respaldo() != null) {
            var stored = result.respaldo();
            values.put("respaldo", Map.of("capturadoEn", stored.capturadoEn().toString(),
                    "edadSegundos", stored.edad().getSeconds(), "antiguo", stored.antiguo(),
                    "versionEsquema", stored.versionEsquema(), "cobertura", stored.cobertura()));
        }
        if (result.lectura() != null) values.put("lectura", result.lectura().name());
        if (result.escritura() != null) values.put("escritura", result.escritura().name());
        return json.convertValue(values, LkgMetadata.class);
    }

    public HttpStatus status(ResultadoLkg<?> result) {
        if (availableSenace(result)) return HttpStatus.OK;
        return "FORMATO_INVALIDO".equals(result.intentoApi().estado())
                || "FORMATO_INVALIDO".equals(result.intentoApi().motivo())
                ? HttpStatus.BAD_GATEWAY : HttpStatus.SERVICE_UNAVAILABLE;
    }

    public HttpHeaders headers(ResultadoLkg<?> result, ConsultaSenace consulta) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Senace-Origen", consulta.getOrigen().toString());
        headers.set("X-Senace-Estado", consulta.getEstado().toString());
        headers.set("X-Senace-Cobertura", consulta.getCobertura().toString());
        if (consulta.getCuerpoCompleto() != null) headers.set("X-Senace-Cuerpo-Completo", consulta.getCuerpoCompleto().toString());
        if (consulta.getMotivo() != null) headers.set("X-Senace-Motivo", consulta.getMotivo().toString());
        headers.set(ATTEMPT, consulta.getLkg().getIntentoApi().getEstado().toString());
        String reason = safeReason(result.intentoApi().motivo());
        if (reason != null) headers.set(REASON, reason);
        if (result.respaldo() != null) {
            headers.set(CAPTURE, result.respaldo().capturadoEn().toString());
            headers.set(AGE, Long.toString(result.respaldo().edad().getSeconds()));
            headers.set(STALE, Boolean.toString(result.respaldo().antiguo()));
        }
        if (result.lectura() != null) headers.set(READ, result.lectura().name());
        if (result.escritura() != null) headers.set(WRITE, result.escritura().name());
        headers.setCacheControl("no-store");
        return headers;
    }

    public EstadoModuloLkg monitor(Map<String, Object> observations) {
        return json.convertValue(observations, EstadoModuloLkg.class);
    }

    public static String safeReason(String reason) {
        if (reason == null) return null;
        if (Arrays.stream(ResultadoConsultaSenace.Motivo.values()).anyMatch(value -> value.name().equals(reason))
                || List.of("SIN_OBSERVACION", "PERIODO_FUERA_DE_RANGO", "SECTOR_NO_SOPORTADO", "ANIO_INVALIDO").contains(reason)) {
            return reason;
        }
        return "ERROR_EXTERNO";
    }
}
