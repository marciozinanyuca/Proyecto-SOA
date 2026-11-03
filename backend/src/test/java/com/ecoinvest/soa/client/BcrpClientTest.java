package com.ecoinvest.soa.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

// HTTP simulado sobre RestTemplate real. No inicia Spring, no usa red ni SQLite y no prueba AOP.
class BcrpClientTest {
    private static final String BASE_URL = "https://bcrp.invalid/api";
    private static final String TITULO = "Producto bruto interno por sectores productivos (variación porcentual interanual)";
    private static final String HIDROCARBUROS = "Minería e Hidrocarburos - Hidrocarburos";
    private ObjectMapper mapper;
    private MockRestServiceServer server;
    private BcrpClient client;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new BcrpClient(restTemplate, mapper);
        ReflectionTestUtils.setField(client, "bcrpBaseUrl", BASE_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "AGRICULTURA,PM04969AA,Agropecuario - Agrícola,SECTORIAL,3.05113403435524",
            "PESCA,PM04971AA,Pesca,SECTORIAL,4.66525619320596",
            "MINERIA,PM04973AA,Minería e Hidrocarburos - Minería Metálica,SECTORIAL,4.4758295916302",
            "MINERÍA,PM04973AA,Minería e Hidrocarburos - Minería Metálica,SECTORIAL,4.4758295916302",
            "HIDROCARBUROS,PM04974AA,Minería e Hidrocarburos - Hidrocarburos,SECTORIAL,-2.44780196733593",
            "ELECTRICIDAD,PM04978AA,Electricidad y Agua,SECTORIAL,1.13808221876177",
            "TODOS,PM04983AA,PBI,NACIONAL,2.51883545370886"
    })
    void approvedCatalogUsesAnnualUrlAndPublicEconomicScope(String sector, String code, String economicSector,
                                                           String scope, String value) throws Exception {
        expect(code, 2017, response(economicSector, "2017", value), MediaType.APPLICATION_JSON);
        Map<String, Object> result = client.obtenerDatosBcrp(sector, 2017);
        assertThat(result).containsEntry("codigoSerie", code).containsEntry("serie", code)
                .containsEntry("descripcion", TITULO + " - " + economicSector)
                .containsEntry("sectorSolicitado", sector).containsEntry("sectorEconomico", economicSector)
                .containsEntry("ambito", scope).containsEntry("periodicidad", "ANUAL")
                .containsEntry("unidad", "Variación porcentual interanual (%)")
                .containsEntry("anioSolicitado", 2017).containsEntry("periodo", "2017")
                .containsEntry("urlFuente", url(code, 2017)).containsEntry("origen", "API_DIRECTA_BCRP");
        assertThat((BigDecimal) result.get("valor")).isEqualByComparingTo(value);
        assertThat(result.get("variacionSeleccionada")).isEqualTo(result.get("valor"));
        assertThat(result).doesNotContainKey("motivo");
    }

    @Test
    void selectedYearChangesRequestAndObservationWithoutTakingLastRow() throws Exception {
        // Dos valores oficiales de hidrocarburos del reporte, deliberadamente en distinto orden.
        String body = "{\"config\":{\"series\":[{\"name\":\"" + TITULO + " - " + HIDROCARBUROS
                + "\"}]},\"periods\":[{\"name\":\"2018\",\"values\":[\"-0.0345569324875896\"]},"
                + "{\"name\":\"2017\",\"values\":[\"-2.44780196733593\"]}]}";
        expect("PM04974AA", 2018, body, MediaType.TEXT_HTML);
        Map<String, Object> result = client.obtenerDatosBcrp("  hidrocarburos  ", 2018);
        assertThat(result).containsEntry("periodo", "2018").containsEntry("anioSolicitado", 2018);
        assertThat((BigDecimal) result.get("valor")).isEqualByComparingTo("-0.0345569324875896");
    }

    @Test
    void validJsonWithHtmlContentTypeIsReadWithoutGlobalConverterChanges() throws Exception {
        expect("PM04974AA", 2017, response(HIDROCARBUROS, "2017", "-2.44780196733593"), MediaType.TEXT_HTML);
        Map<String, Object> result = client.consultarBcrp();
        assertThat(result).containsEntry("origen", "API_DIRECTA_BCRP");
        assertThat((BigDecimal) result.get("valor")).isEqualByComparingTo("-2.44780196733593");
    }

    @Test
    void neighboringYearDoesNotReplaceRequestedYear() throws Exception {
        expect("PM04974AA", 2018, response(HIDROCARBUROS, "2017", "-2.44780196733593"), MediaType.APPLICATION_JSON);
        assertAbsent(client.obtenerDatosBcrp("Hidrocarburos", 2018), "PERIODO_FUERA_DE_RANGO");
    }

    @Test
    void emptyPeriodsMeanNoObservation() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(response(HIDROCARBUROS, "2017", "-2.44780196733593"));
        body.set("periods", mapper.createArrayNode());
        expect("PM04974AA", 2017, body.toString(), MediaType.TEXT_HTML);
        assertAbsent(client.consultarBcrp(), "SIN_OBSERVACION");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void emptyHttpBodyHasNoObservation(String body) {
        expect("PM04974AA", 2017, body == null ? "" : body, MediaType.TEXT_HTML);
        assertAbsent(client.consultarBcrp(), "SIN_OBSERVACION");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html><body>Error</body></html>", "{invalid", "{} {}", "null", "[]", "{}"})
    void rejectsHtmlInvalidJsonAndUnexpectedRoots(String body) {
        expect("PM04974AA", 2017, body, MediaType.TEXT_HTML);
        assertAbsent(client.consultarBcrp(), "FORMATO_INVALIDO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sinConfig", "sinNombre", "masSeries", "sinPeriods", "periodsNoLista",
            "filaNoObjeto", "fechaMensual", "sinValores", "valoresNoLista", "dosColumnas",
            "ceroColumnas", "valorBooleano", "serieIncorrecta", "periodoDuplicado"})
    void rejectsMalformedStructuresAndAmbiguousPeriods(String mutation) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(response(HIDROCARBUROS, "2017", "-2.44780196733593"));
        ArrayNode series = (ArrayNode) body.path("config").path("series");
        ArrayNode periods = (ArrayNode) body.path("periods");
        ObjectNode period = (ObjectNode) periods.get(0);
        switch (mutation) {
            case "sinConfig" -> body.remove("config");
            case "sinNombre" -> ((ObjectNode) series.get(0)).remove("name");
            case "masSeries" -> series.add(series.get(0).deepCopy());
            case "sinPeriods" -> body.remove("periods");
            case "periodsNoLista" -> body.put("periods", "unexpected");
            case "filaNoObjeto" -> periods.set(0, mapper.getNodeFactory().textNode("unexpected"));
            case "fechaMensual" -> period.put("name", "Ene.2017");
            case "sinValores" -> period.remove("values");
            case "valoresNoLista" -> period.put("values", "unexpected");
            case "dosColumnas" -> ((ArrayNode) period.path("values")).add("0");
            case "ceroColumnas" -> period.set("values", mapper.createArrayNode());
            case "valorBooleano" -> ((ArrayNode) period.path("values")).set(0, mapper.getNodeFactory().booleanNode(true));
            case "serieIncorrecta" -> ((ObjectNode) series.get(0)).put("name", "Otra serie");
            case "periodoDuplicado" -> periods.add(period.deepCopy());
            default -> throw new AssertionError("Mutación no prevista");
        }
        expect("PM04974AA", 2017, body.toString(), MediaType.APPLICATION_JSON);
        assertAbsent(client.consultarBcrp(), "FORMATO_INVALIDO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"n.d.", "", "NaN", "Infinity", "-Infinity", "1,2"})
    void nonNumericValuesAreNeverReplacedByConstants(String value) throws Exception {
        expect("PM04974AA", 2017, response(HIDROCARBUROS, "2017", value), MediaType.APPLICATION_JSON);
        assertAbsent(client.consultarBcrp(), "FORMATO_INVALIDO");
    }

    @Test
    void nullValueMeansNoObservation() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(response(HIDROCARBUROS, "2017", "-2.44780196733593"));
        ((ArrayNode) body.path("periods").get(0).path("values")).set(0, mapper.getNodeFactory().nullNode());
        expect("PM04974AA", 2017, body.toString(), MediaType.APPLICATION_JSON);
        assertAbsent(client.consultarBcrp(), "SIN_OBSERVACION");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.0345569324875896", "0.12345678901234567890123456789"})
    void decimalStringsPreservePrecisionIncludingZeroAndNegativeValues(String value) throws Exception {
        // El decimal largo es un caso técnico de precisión, no una observación económica atribuida al BCRP.
        expect("PM04974AA", 2017, response(HIDROCARBUROS, "2017", value), MediaType.APPLICATION_JSON);
        assertThat((BigDecimal) client.consultarBcrp().get("valor")).isEqualByComparingTo(value);
    }

    @Test
    void jsonNumericDecimalAlsoPreservesPrecision() throws Exception {
        String value = "0.12345678901234567890123456789";
        String body = response(HIDROCARBUROS, "2017", value).replace("\"" + value + "\"", value);
        expect("PM04974AA", 2017, body, MediaType.APPLICATION_JSON);
        assertThat((BigDecimal) client.consultarBcrp().get("valor")).isEqualByComparingTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"TRANSPORTES", "ENERGIA", "Energía", "Desconocido", "   "})
    void unsupportedSectorsHaveNoSeriesOrHttpRequest(String sector) {
        Map<String, Object> result = client.obtenerDatosBcrp(sector, 2017);
        assertAbsent(result, "SECTOR_NO_SOPORTADO");
        assertThat(result).doesNotContainKeys("codigoSerie", "urlFuente", "serie");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 10000})
    void invalidYearDoesNotMakeHttpRequest(int year) {
        assertAbsent(client.obtenerDatosBcrp("Hidrocarburos", year), "ANIO_INVALIDO");
    }

    @Test
    void nullYearRetainsExisting2017Default() throws Exception {
        expect("PM04974AA", 2017, response(HIDROCARBUROS, "2017", "-2.44780196733593"), MediaType.APPLICATION_JSON);
        assertThat(client.obtenerDatosBcrp("Hidrocarburos", null)).containsEntry("periodo", "2017");
    }

    @Test
    void httpErrorsContinueToPropagateToExistingResilienceBoundary() {
        server.expect(requestTo(url("PM04974AA", 2017))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.consultarBcrp()).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void connectionErrorsContinueToPropagateToExistingResilienceBoundary() {
        server.expect(requestTo(url("PM04974AA", 2017))).andRespond(withException(new IOException("connection error")));
        assertThatThrownBy(() -> client.consultarBcrp()).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void directFallbackPreservesAnnualMetadataWithoutSyntheticObservation() {
        Map<String, Object> result = client.fallbackBcrpPorSector("TODOS", 2018, new RuntimeException("external error"));
        assertAbsent(result, "ERROR_EXTERNO");
        assertThat(result).containsEntry("codigoSerie", "PM04983AA").containsEntry("ambito", "NACIONAL")
                .containsEntry("sectorEconomico", "PBI").containsEntry("periods", List.of())
                .containsEntry("urlFuente", url("PM04983AA", 2018));
        assertThat(result.toString()).doesNotContain("SQLite", "external error");
        assertAbsent(client.fallbackBcrp(new RuntimeException()), "ERROR_EXTERNO");
    }

    private void expect(String code, int year, String body, MediaType contentType) {
        server.expect(requestTo(url(code, year))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, new MediaType(contentType, StandardCharsets.UTF_8)));
    }

    private String url(String code, int year) {
        return BASE_URL + "/" + code + "/json/" + year + "/" + year + "/esp";
    }

    private String response(String economicSector, String period, String value) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "config", Map.of("title", TITULO, "series", List.of(Map.of("name", TITULO + " - " + economicSector, "dec", "1"))),
                "periods", List.of(Map.of("name", period, "values", List.of(value)))));
    }

    private void assertAbsent(Map<String, Object> result, String reason) {
        assertThat(result).containsEntry("origen", "NO_DISPONIBLE").containsEntry("motivo", reason)
                .containsEntry("valor", null).containsEntry("variacionSeleccionada", null).containsEntry("periodo", null);
    }
}
