package com.ecoinvest.soa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.SenaceClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// MVC standalone: sin auto-configuración, SQLite ni APIs externas.
class EcoInvestControllerTest {
    private SenaceClient senace;
    private BcrpClient bcrp;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        senace = mock(SenaceClient.class);
        bcrp = mock(BcrpClient.class);
        mvc = MockMvcBuilders.standaloneSetup(new EcoInvestController(
                senace, bcrp, CircuitBreakerRegistry.ofDefaults()))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).build();
    }

    @Test
    void dashboardDoesNotInventAnIndicatorWhenClientHasNoData() throws Exception {
        when(senace.obtenerProyectos("Hidrocarburos", 2017)).thenReturn(List.of());
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenReturn(null);
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.variacionSeleccionada").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.fuentes.senace").value("SIMULADO"))
                .andExpect(jsonPath("$.fuentes.bcrp").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.periodo").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.origen").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.indicadorBcrp.motivo").value("ERROR_EXTERNO"));
    }

    @Test
    void annualIndicatorIsTraceableAndPreservesExistingDashboardFields() throws Exception {
        when(senace.obtenerProyectos("Hidrocarburos", 2018))
                .thenReturn(List.of(Map.of("estado", "Aprobado", "origen", "SIMULADO")));
        BigDecimal value = new BigDecimal("-0.0345569324875896");
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2018)).thenReturn(economicData(
                "PM04974AA", "Minería e Hidrocarburos - Hidrocarburos", "SECTORIAL", 2018, value));
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos").param("anio", "2018"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.aprobados").value(1)).andExpect(jsonPath("$.proyectos[0].estado").value("Aprobado"))
                .andExpect(jsonPath("$.variacionSeleccionada").value(value.doubleValue()))
                .andExpect(jsonPath("$.fuentes.bcrp").value("API_DIRECTA_BCRP"))
                .andExpect(jsonPath("$.fuentes.senace").value("SIMULADO"))
                .andExpect(jsonPath("$.fuentes.anioConsultado").value(2018))
                .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04974AA"))
                .andExpect(jsonPath("$.indicadorBcrp.descripcion").value(
                        "Producto bruto interno por sectores productivos (variación porcentual interanual) - Minería e Hidrocarburos - Hidrocarburos"))
                .andExpect(jsonPath("$.indicadorBcrp.unidad").value("Variación porcentual interanual (%)"))
                .andExpect(jsonPath("$.indicadorBcrp.periodicidad").value("ANUAL"))
                .andExpect(jsonPath("$.indicadorBcrp.sectorSolicitado").value("Hidrocarburos"))
                .andExpect(jsonPath("$.indicadorBcrp.sectorEconomico").value("Minería e Hidrocarburos - Hidrocarburos"))
                .andExpect(jsonPath("$.indicadorBcrp.ambito").value("SECTORIAL"))
                .andExpect(jsonPath("$.indicadorBcrp.anioSolicitado").value(2018))
                .andExpect(jsonPath("$.indicadorBcrp.periodo").value("2018"))
                .andExpect(jsonPath("$.indicadorBcrp.valor").value(value.doubleValue()))
                .andExpect(jsonPath("$.indicadorBcrp.origen").value("API_DIRECTA_BCRP"))
                .andExpect(jsonPath("$.indicadorBcrp.urlFuente").value(
                        "https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04974AA/json/2018/2018/esp"))
                .andExpect(jsonPath("$.indicadorBcrp.motivo").doesNotHaveJsonPath());
    }

    @Test
    void bothEconomicValuesKeepExactDecimalPrecisionThroughSerialization() throws Exception {
        // Caso técnico de precisión; no se atribuye esta cifra a una observación oficial.
        BigDecimal value = new BigDecimal("0.12345678901234567890123456789");
        when(senace.obtenerProyectos("Hidrocarburos", 2017)).thenReturn(List.of());
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenReturn(economicData(
                "PM04974AA", "Minería e Hidrocarburos - Hidrocarburos", "SECTORIAL", 2017, value));
        String json = mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(json);
        assertThat(response.get("variacionSeleccionada").decimalValue()).isEqualByComparingTo(value);
        assertThat(response.path("indicadorBcrp").get("valor").decimalValue()).isEqualByComparingTo(value);
    }

    @Test
    void nonFiniteUnexpectedClientValueIsNotConvertedToBigDecimal() throws Exception {
        when(senace.obtenerProyectos("Hidrocarburos", 2017)).thenReturn(List.of());
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017))
                .thenReturn(Map.of("valor", Double.NaN, "origen", "API_DIRECTA_BCRP"));
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variacionSeleccionada").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.fuentes.bcrp").value("NO_DISPONIBLE"));
    }

    @Test
    void missingObservationKeepsMetadataButDoesNotPublishAnEffectivePeriodOrValue() throws Exception {
        when(senace.obtenerProyectos("Electricidad", 2017)).thenReturn(List.of());
        Map<String, Object> data = economicData("PM04978AA", "Electricidad y Agua", "SECTORIAL", 2017, null);
        data.put("motivo", "SIN_OBSERVACION");
        when(bcrp.obtenerDatosBcrp("Electricidad", 2017)).thenReturn(data);
        mvc.perform(get("/api/dashboard").param("sector", "Electricidad"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variacionSeleccionada").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.periodo").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04978AA"))
                .andExpect(jsonPath("$.indicadorBcrp.sectorEconomico").value("Electricidad y Agua"))
                .andExpect(jsonPath("$.indicadorBcrp.motivo").value("SIN_OBSERVACION"))
                .andExpect(jsonPath("$.fuentes.bcrp").value("NO_DISPONIBLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Transportes", "Energía", "Sector desconocido"})
    void unsupportedSectorIsExplicitlyAbsent(String sector) throws Exception {
        when(senace.obtenerProyectos(sector, 2017)).thenReturn(List.of());
        when(bcrp.obtenerDatosBcrp(sector, 2017))
                .thenReturn(Map.of("origen", "NO_DISPONIBLE", "motivo", "SECTOR_NO_SOPORTADO"));
        mvc.perform(get("/api/dashboard").param("sector", sector))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variacionSeleccionada").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.motivo").value("SECTOR_NO_SOPORTADO"));
    }

    @Test
    void todosUsesNationalPbiWithoutChangingSenaceResults() throws Exception {
        when(senace.obtenerProyectos("TODOS", 2017)).thenReturn(List.of(Map.of("origen", "SIMULADO", "estado", "Aprobado")));
        when(bcrp.obtenerDatosBcrp("TODOS", 2017))
                .thenReturn(economicData("PM04983AA", "PBI", "NACIONAL", 2017, new BigDecimal("2.51883545370886")));
        mvc.perform(get("/api/dashboard").param("sector", "TODOS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.fuentes.senace").value("SIMULADO"))
                .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04983AA"))
                .andExpect(jsonPath("$.indicadorBcrp.ambito").value("NACIONAL"))
                .andExpect(jsonPath("$.indicadorBcrp.sectorEconomico").value("PBI"));
    }

    @Test
    void otherThreeEndpointsPreserveTheirResponseShapes() throws Exception {
        when(senace.obtenerConsultoras()).thenReturn(List.of(Map.of("origen", "SIMULADO")));
        when(senace.consultarDatastream("CarteraProyectos"))
                .thenReturn(Map.of("datastream", "CarteraProyectos", "origen", "API_DIRECTA_SENACE"));
        mvc.perform(get("/api/consultoras")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origen").value("SIMULADO"));
        mvc.perform(get("/api/senace/datastream")).andExpect(status().isOk())
                .andExpect(jsonPath("$.datastream").value("CarteraProyectos"));
        mvc.perform(get("/api/estado-servicios")).andExpect(status().isOk())
                .andExpect(jsonPath("$.senace.respaldoLocal").value("NO_IMPLEMENTADO"))
                .andExpect(jsonPath("$.bcrp.respaldoLocal").value("NO_IMPLEMENTADO"));
    }

    private Map<String, Object> economicData(String code, String economicSector, String scope, int year, BigDecimal value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("codigoSerie", code);
        data.put("descripcion", "Producto bruto interno por sectores productivos (variación porcentual interanual) - " + economicSector);
        data.put("sectorEconomico", economicSector);
        data.put("unidad", "Variación porcentual interanual (%)");
        data.put("periodicidad", "ANUAL");
        data.put("ambito", scope);
        data.put("anioSolicitado", year);
        data.put("periodo", value != null ? String.valueOf(year) : null);
        data.put("valor", value);
        data.put("variacionSeleccionada", value);
        data.put("origen", value != null ? "API_DIRECTA_BCRP" : "NO_DISPONIBLE");
        data.put("urlFuente", "https://estadisticas.bcrp.gob.pe/estadisticas/series/api/" + code + "/json/" + year + "/" + year + "/esp");
        return data;
    }
}
