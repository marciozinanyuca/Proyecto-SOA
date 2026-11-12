package com.ecoinvest.soa.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BcrpClient {

    private static final Logger log = LoggerFactory.getLogger(BcrpClient.class);
    private static final String TITULO = "Producto bruto interno por sectores productivos (variación porcentual interanual)";

    // Catálogo anual aprobado en Fase 2B. El alcance económico no se deduce del filtro SENACE.
    private record Serie(String codigo, String sectorEconomico, String ambito) {
        String descripcion() {
            return TITULO + " - " + sectorEconomico;
        }
    }

    private static final Map<String, Serie> SERIES_POR_SECTOR = Map.of(
            "AGRICULTURA", new Serie("PM04969AA", "Agropecuario - Agrícola", "SECTORIAL"),
            "PESCA", new Serie("PM04971AA", "Pesca", "SECTORIAL"),
            "MINERIA", new Serie("PM04973AA", "Minería e Hidrocarburos - Minería Metálica", "SECTORIAL"),
            "HIDROCARBUROS", new Serie("PM04974AA", "Minería e Hidrocarburos - Hidrocarburos", "SECTORIAL"),
            "ELECTRICIDAD", new Serie("PM04978AA", "Electricidad y Agua", "SECTORIAL"),
            "TODOS", new Serie("PM04983AA", "PBI", "NACIONAL")
    );

    @Value("${external.apis.bcrp-base-url:https://estadisticas.bcrp.gob.pe/estadisticas/series/api}")
    private String bcrpBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BcrpClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "bcrp", fallbackMethod = "fallbackBcrp")
    public Map<String, Object> consultarBcrp() {
        return ejecutarConsulta("Hidrocarburos", 2017);
    }

    @CircuitBreaker(name = "bcrp", fallbackMethod = "fallbackBcrpPorSector")
    public Map<String, Object> obtenerDatosBcrp(String sector, Integer anio) {
        return ejecutarConsulta(sector, anio);
    }

    private Map<String, Object> ejecutarConsulta(String sector, Integer anio) {
        Map<String, Object> result = resultadoBase(sector, anio);
        if (result.get("motivo") != null) {
            return result;
        }
        Serie serie = SERIES_POR_SECTOR.get(normalizarSector(sector));
        int anioSolicitado = (Integer) result.get("anioSolicitado");
        log.info("Consultando BCRP: serie {}, año {}", serie.codigo(), anioSolicitado);
        // String permite leer JSON servido como text/html sin cambiar el RestTemplate compartido.
        // Los errores HTTP/conexión siguen propagándose al CircuitBreaker existente.
        String body = restTemplate.getForObject((String) result.get("urlFuente"), String.class);
        if (body == null || body.isBlank()) {
            result.put("motivo", "SIN_OBSERVACION");
            return result;
        }
        try {
            JsonNode response = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(body);
            return interpretarRespuesta(response, serie, anioSolicitado, result);
        } catch (JsonProcessingException e) {
            log.warn("Respuesta BCRP con formato JSON inválido");
            result.put("motivo", "FORMATO_INVALIDO");
            return result;
        }
    }

    private Map<String, Object> interpretarRespuesta(JsonNode response, Serie serie, int anio,
                                                     Map<String, Object> result) {
        JsonNode series = response == null ? null : response.path("config").path("series");
        JsonNode periods = response == null ? null : response.get("periods");
        if (response == null || !response.isObject() || series == null || !series.isArray()
                || series.size() != 1 || !series.get(0).isObject()
                || !serie.descripcion().equals(series.get(0).path("name").asText())
                || periods == null || !periods.isArray()) {
            result.put("motivo", "FORMATO_INVALIDO");
            return result;
        }
        if (periods.isEmpty()) {
            result.put("motivo", "SIN_OBSERVACION");
            return result;
        }
        String periodoEsperado = String.format(Locale.ROOT, "%04d", anio);
        JsonNode seleccionado = null;
        for (JsonNode period : periods) {
            JsonNode name = period.get("name");
            JsonNode values = period.get("values");
            if (!period.isObject() || name == null || !name.isTextual()
                    || !name.textValue().matches("[0-9]{4}")
                    || values == null || !values.isArray() || values.size() != 1) {
                result.put("motivo", "FORMATO_INVALIDO");
                return result;
            }
            if (periodoEsperado.equals(name.textValue())) {
                if (seleccionado != null) {
                    result.put("motivo", "FORMATO_INVALIDO");
                    return result;
                }
                seleccionado = values.get(0);
            }
        }
        if (seleccionado == null) {
            result.put("motivo", "PERIODO_FUERA_DE_RANGO");
            return result;
        }
        if (seleccionado.isNull()) {
            result.put("motivo", "SIN_OBSERVACION");
            return result;
        }
        if (!seleccionado.isTextual() && !seleccionado.isNumber()) {
            result.put("motivo", "FORMATO_INVALIDO");
            return result;
        }
        try {
            BigDecimal valor = new BigDecimal(seleccionado.asText().trim());
            result.put("valor", valor);
            result.put("variacionSeleccionada", valor);
            result.put("periodo", periodoEsperado);
            result.put("origen", "API_DIRECTA_BCRP");
            result.put("periods", List.of(Map.of("name", periodoEsperado,
                    "values", List.of(valor.toString()))));
            return result;
        } catch (NumberFormatException e) {
            result.put("motivo", "FORMATO_INVALIDO");
            return result;
        }
    }

    /** Contexto interno sin HTTP, para reconstruir metadatos desde el catalogo vigente. */
    Map<String, Object> contextoConsulta(String sector, Integer anio) {
        return resultadoBase(sector, anio);
    }

    private Map<String, Object> resultadoBase(String sector, Integer anio) {
        int anioSolicitado = anio != null ? anio : 2017;
        Serie serie = SERIES_POR_SECTOR.get(normalizarSector(sector));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sectorSolicitado", sector != null ? sector : "");
        result.put("anioSolicitado", anioSolicitado);
        // Se conservan las claves internas utilizadas por la integración previa.
        result.put("sectorConsultado", result.get("sectorSolicitado"));
        result.put("anioConsultado", anioSolicitado);
        result.put("origen", "NO_DISPONIBLE");
        result.put("valor", null);
        result.put("variacionSeleccionada", null);
        result.put("periodo", null);
        result.put("periods", List.of());
        if (serie == null) {
            result.put("motivo", "SECTOR_NO_SOPORTADO");
            return result;
        }
        result.put("codigoSerie", serie.codigo());
        result.put("serie", serie.codigo());
        result.put("descripcion", serie.descripcion());
        result.put("unidad", "Variación porcentual interanual (%)");
        result.put("periodicidad", "ANUAL");
        result.put("sectorEconomico", serie.sectorEconomico());
        result.put("ambito", serie.ambito());
        if (anioSolicitado < 1 || anioSolicitado > 9999) {
            result.put("motivo", "ANIO_INVALIDO");
            return result;
        }
        result.put("urlFuente", String.format(Locale.ROOT, "%s/%s/json/%04d/%04d/esp",
                bcrpBaseUrl.replaceAll("/+$", ""), serie.codigo(), anioSolicitado, anioSolicitado));
        return result;
    }

    private String normalizarSector(String sector) {
        return sector == null ? "" : Normalizer.normalize(sector.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    public Map<String, Object> fallbackBcrp(Throwable t) {
        return fallbackBcrpPorSector("Hidrocarburos", 2017, t);
    }

    public Map<String, Object> fallbackBcrpPorSector(String sector, Integer anio, Throwable t) {
        log.warn("Fallo al consultar BCRP; indicador no disponible, sin respaldo persistente implementado");
        Map<String, Object> result = resultadoBase(sector, anio);
        if (result.get("motivo") == null) {
            result.put("motivo", "ERROR_EXTERNO");
        }
        result.put("mensaje", "Indicador no disponible; no hay respaldo persistente implementado");
        return result;
    }
}
