package com.ecoinvest.soa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.ResultadoConsultaSenace;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Conteos;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoConsulta;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoFiltrado;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Motivo;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Origen;
import com.ecoinvest.soa.client.SenaceClient;
import com.ecoinvest.soa.client.SenaceMapper;
import com.ecoinvest.soa.client.SenaceMapper.ConsultoraMapeada;
import com.ecoinvest.soa.client.SenaceMapper.ProyectoMapeado;
import com.ecoinvest.soa.client.SenaceTransport;
import com.ecoinvest.soa.client.dto.ConsultoraSenaceDto;
import com.ecoinvest.soa.client.dto.ProyectoSenaceDto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** MockMvc y contexto MVC minimo para preflight, con fixtures: sin servidor, proveedores o SQLite. */
class EcoInvestControllerTest {
    private SenaceClient senace;
    private BcrpClient bcrp;
    private CircuitBreakerRegistry registry;
    private MockMvc mvc;
    private final SenaceMapper mapper = new SenaceMapper();
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @BeforeEach void setUp() {
        senace = mock(SenaceClient.class);
        bcrp = mock(BcrpClient.class);
        registry = CircuitBreakerRegistry.ofDefaults();
        ObjectMapper mvcJackson = Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JsonNullableModule()).build();
        mvc = MockMvcBuilders.standaloneSetup(new EcoInvestController(senace, bcrp, registry))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mvcJackson))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).build();
    }

    @Test void availableDashboardKeepsTypedRowsAndBothProviders() throws Exception {
        when(senace.consultarProyectos("Hidrocarburos", 2018)).thenReturn(projects(List.of(
                project(1, "Hidrocarburos", "01/01/2018", "Aprobado"),
                project(2, "Hidrocarburos", "01/02/2018", "En Evaluación")), "Hidrocarburos", 2018));
        BigDecimal value = new BigDecimal("-0.0345569324875896"); // Fixture, no consulta oficial nueva.
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2018)).thenReturn(economicData("PM04974AA",
                "Minería e Hidrocarburos - Hidrocarburos", "SECTORIAL", 2018, value));
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos").param("anio", "2018"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.aprobados").value(1)).andExpect(jsonPath("$.proyectos.length()").value(2))
                .andExpect(jsonPath("$.proyectos[0].titulo").value("PROYECTO ARTIFICIAL"))
                .andExpect(jsonPath("$.proyectos[0].proyecto").value("PROYECTO ARTIFICIAL"))
                .andExpect(jsonPath("$.proyectos[0].fechaInicio").value("01/01/2018"))
                .andExpect(jsonPath("$.proyectos[0].anio").value(2018))
                .andExpect(jsonPath("$.proyectos[0].ruc").value("00000000001"))
                .andExpect(jsonPath("$.proyectos[0].origen").value("API_DIRECTA_SENACE"))
                .andExpect(jsonPath("$.fuentes.senace").value("API_DIRECTA_SENACE"))
                .andExpect(jsonPath("$.fuentes.bcrp").value("API_DIRECTA_BCRP"))
                .andExpect(jsonPath("$.consultaSenace.estado").value("EXITO"))
                .andExpect(jsonPath("$.consultaSenace.cuerpoCompleto").value(true))
                .andExpect(jsonPath("$.consultaSenace.cobertura").value("NO_VERIFICADA"))
                .andExpect(jsonPath("$.consultaSenace.conteos.devueltos").value(2))
                .andExpect(jsonPath("$.consultaSenace.motivo").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04974AA"))
                .andExpect(jsonPath("$.indicadorBcrp.descripcion").value(
                        "Producto bruto interno por sectores productivos (variación porcentual interanual) - Minería e Hidrocarburos - Hidrocarburos"))
                .andExpect(jsonPath("$.indicadorBcrp.sectorSolicitado").value("Hidrocarburos"))
                .andExpect(jsonPath("$.indicadorBcrp.sectorEconomico").value("Minería e Hidrocarburos - Hidrocarburos"))
                .andExpect(jsonPath("$.indicadorBcrp.anioSolicitado").value(2018))
                .andExpect(jsonPath("$.indicadorBcrp.origen").value("API_DIRECTA_BCRP"))
                .andExpect(jsonPath("$.indicadorBcrp.urlFuente").value(
                        "https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04974AA/json/2018/2018/esp"))
                .andExpect(jsonPath("$.indicadorBcrp.periodo").value("2018"))
                .andExpect(jsonPath("$.indicadorBcrp.unidad").value("Variación porcentual interanual (%)"))
                .andExpect(jsonPath("$.indicadorBcrp.periodicidad").value("ANUAL"))
                .andExpect(jsonPath("$.indicadorBcrp.ambito").value("SECTORIAL"))
                .andExpect(jsonPath("$.indicadorBcrp.motivo").doesNotHaveJsonPath());
        verify(senace).consultarProyectos("Hidrocarburos", 2018);
        verify(senace, never()).obtenerProyectos(any(), any());
        verify(bcrp).obtenerDatosBcrp("Hidrocarburos", 2018);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ERROR_EXTERNO", "CONFIGURACION_AUSENTE", "CONFIGURACION_INVALIDA",
            "CIRCUITO_ABIERTO", "TIMEOUT_INACTIVIDAD", "FORMATO_INVALIDO", "LIMITE_LECTURA"})
    void senaceFailuresDoNotPreventBcrpAndNeverBecomeZeroMetrics(String reason) throws Exception {
        Motivo motivo = Motivo.valueOf(reason);
        EstadoConsulta estado = motivo == Motivo.FORMATO_INVALIDO ? EstadoConsulta.FORMATO_INVALIDO
                : motivo == Motivo.LIMITE_LECTURA ? EstadoConsulta.PARCIAL : EstadoConsulta.ERROR_EXTERNO;
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(failure(estado, motivo, null));
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenReturn(economicData("PM04974AA",
                "Hidrocarburos", "SECTORIAL", 2017, new BigDecimal("1.234567890123456789")));
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.aprobados").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.proyectos").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.fuentes.senace").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.fuentes.bcrp").value("API_DIRECTA_BCRP"))
                .andExpect(jsonPath("$.consultaSenace.estado").value(estado.name()))
                .andExpect(jsonPath("$.consultaSenace.motivo").value(reason))
                .andExpect(jsonPath("$.consultaSenace.cuerpoCompleto").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.consultaSenace.conteos.recibidos").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04974AA"))
                .andExpect(jsonPath("$.indicadorBcrp.periodo").value("2017"));
        verify(senace).consultarProyectos("Hidrocarburos", 2017);
        verify(bcrp).obtenerDatosBcrp("Hidrocarburos", 2017);
    }

    @Test void officialEmptyHasOfficialOriginEvenWithoutFirstRow() throws Exception {
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(projects(List.of(), "Hidrocarburos", 2017));
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.aprobados").value(0)).andExpect(jsonPath("$.proyectos").isEmpty())
                .andExpect(jsonPath("$.fuentes.senace").value("API_DIRECTA_SENACE"))
                .andExpect(jsonPath("$.consultaSenace.estado").value("VACIA"))
                .andExpect(jsonPath("$.consultaSenace.motivo").value("RESPUESTA_OFICIAL_VACIA"));
    }

    @Test void noFilterMatchesDifferFromOfficialEmpty() throws Exception {
        when(senace.consultarProyectos("Pesca", 2017)).thenReturn(projects(
                List.of(project(1, "Minería", "01/01/2017", "Aprobado")), "Pesca", 2017));
        mvc.perform(get("/api/dashboard").param("sector", "Pesca"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.consultaSenace.estado").value("EXITO"))
                .andExpect(jsonPath("$.consultaSenace.estadoFiltrado").value("SIN_COINCIDENCIAS"))
                .andExpect(jsonPath("$.consultaSenace.conteos.recibidos").value(1))
                .andExpect(jsonPath("$.consultaSenace.conteos.devueltos").value(0))
                .andExpect(jsonPath("$.fuentes.senace").value("API_DIRECTA_SENACE"));
    }

    @Test void nonEvaluableDatesAndFilteredCountsRemainExplicit() throws Exception {
        when(senace.consultarProyectos("MINERIA", 2017)).thenReturn(projects(List.of(
                project(1, "Minería", "05/04/2017", "Aprobado"),
                project(2, "Minería", "31/02/2017", "Aprobado"),
                project(3, "Minería", "01/01/2018", "Aprobado")), "MINERIA", 2017));
        mvc.perform(get("/api/dashboard").param("sector", "MINERIA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.aprobados").value(1))
                .andExpect(jsonPath("$.proyectos[0].fechaInicio").value("05/04/2017"))
                .andExpect(jsonPath("$.consultaSenace.estadoFiltrado").value("NO_EVALUABLE"))
                .andExpect(jsonPath("$.consultaSenace.conteos.fechasNoEvaluables").value(1))
                .andExpect(jsonPath("$.consultaSenace.conteos.fechasNoEvaluablesEnActividad").value(1))
                .andExpect(jsonPath("$.consultaSenace.advertencias[0]").value("FECHA_INVALIDA"));
    }

    @Test void senaceExceptionsAreSanitizedAndStillAllowEconomicData() throws Exception {
        when(senace.consultarProyectos(any(), any())).thenThrow(
                new RuntimeException("https://fixture.invalid?auth_key=MARCADOR_ARTIFICIAL_NO_PUBLICAR"));
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenReturn(economicData("PM04974AA",
                "Hidrocarburos", "SECTORIAL", 2017, new BigDecimal("1.1")));
        String body = mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consultaSenace.motivo").value("ERROR_EXTERNO"))
                .andExpect(jsonPath("$.fuentes.bcrp").value("API_DIRECTA_BCRP")).andReturn().getResponse().getContentAsString();
        assertSafe(body);
        verify(bcrp).obtenerDatosBcrp("Hidrocarburos", 2017);
    }

    @Test void unavailableOrIncoherentClientResultCannotBePublishedAsOfficialData() throws Exception {
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consultaSenace.estado").value("ERROR_EXTERNO"))
                .andExpect(jsonPath("$.total").doesNotHaveJsonPath());
        var mapped = mapper.mapearProyectos(List.of(project(1, "Hidrocarburos", "01/01/2017", "Aprobado")), "Hidrocarburos", 2017);
        // El mapper solo no aporta evidencia HTTP.
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(mapped);
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consultaSenace.estado").value("FORMATO_INVALIDO"))
                .andExpect(jsonPath("$.proyectos").doesNotHaveJsonPath());
    }

    @Test void senaceAndEconomicDecimalsKeepPrecisionThroughSerialization() throws Exception {
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(projects(
                List.of(project(1, "Hidrocarburos", "01/01/2017", "Aprobado")), "Hidrocarburos", 2017));
        BigDecimal value = new BigDecimal("0.12345678901234567890123456789");
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenReturn(economicData("PM04974AA",
                "Hidrocarburos", "SECTORIAL", 2017, value));
        JsonNode body = json.readTree(mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("variacionSeleccionada").decimalValue()).isEqualByComparingTo(value);
        assertThat(body.get("indicadorBcrp").get("valor").decimalValue()).isEqualByComparingTo(value);
        assertThat(body.get("proyectos").get(0).get("latitud").decimalValue()).isEqualByComparingTo("-12.12345678901234567890");
        assertThat(body.get("proyectos").get(0).get("longitud").decimalValue()).isEqualByComparingTo("-70.12345678901234567890");
    }

    @Test void economicAbsenceRemainsIndependentOfOfficialEmptySenace() throws Exception {
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(projects(List.of(), "Hidrocarburos", 2017));
        // Mockito devuelve Map vacio por defecto; aqui la ausencia de respuesta es null explicito.
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenReturn(null);
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fuentes.senace").value("API_DIRECTA_SENACE"))
                .andExpect(jsonPath("$.fuentes.bcrp").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.variacionSeleccionada").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.periodo").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.motivo").value("ERROR_EXTERNO"));
    }

    @Test void economicMissingObservationKeepsItsMetadata() throws Exception {
        Map<String, Object> data = economicData("PM04978AA", "Electricidad y Agua", "SECTORIAL", 2017, null);
        data.put("motivo", "SIN_OBSERVACION");
        when(bcrp.obtenerDatosBcrp("Electricidad", 2017)).thenReturn(data);
        mvc.perform(get("/api/dashboard").param("sector", "Electricidad"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04978AA"))
                .andExpect(jsonPath("$.indicadorBcrp.sectorEconomico").value("Electricidad y Agua"))
                .andExpect(jsonPath("$.indicadorBcrp.motivo").value("SIN_OBSERVACION"))
                .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.periodo").doesNotHaveJsonPath());
    }

    @Test void nonFiniteEconomicValueIsNotConvertedToDecimal() throws Exception {
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenReturn(Map.of("valor", Double.NaN, "origen", "API_DIRECTA_BCRP"));
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variacionSeleccionada").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.fuentes.bcrp").value("NO_DISPONIBLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Transportes", "Energía", "Sector desconocido"})
    void unsupportedEconomicSectorDoesNotObtainInventedSeries(String sector) throws Exception {
        when(bcrp.obtenerDatosBcrp(sector, 2017)).thenReturn(Map.of("origen", "NO_DISPONIBLE", "motivo", "SECTOR_NO_SOPORTADO"));
        mvc.perform(get("/api/dashboard").param("sector", sector))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indicadorBcrp.codigoSerie").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.indicadorBcrp.motivo").value("SECTOR_NO_SOPORTADO"));
    }

    @Test void todosKeepsNationalPbiAndOriginalSenaceActivities() throws Exception {
        when(senace.consultarProyectos("TODOS", 2017)).thenReturn(projects(
                List.of(project(1, "Minería", "01/01/2017", "Aprobado")), "TODOS", 2017));
        when(bcrp.obtenerDatosBcrp("TODOS", 2017)).thenReturn(economicData("PM04983AA",
                "PBI", "NACIONAL", 2017, new BigDecimal("2.51883545370886")));
        mvc.perform(get("/api/dashboard").param("sector", "TODOS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.proyectos[0].actividad").value("Minería"))
                .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04983AA"))
                .andExpect(jsonPath("$.indicadorBcrp.ambito").value("NACIONAL"));
    }

    @Test void economicUnexpectedExceptionDoesNotEraseSenaceDataOrExposeMessage() throws Exception {
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(projects(List.of(), "Hidrocarburos", 2017));
        when(bcrp.obtenerDatosBcrp("Hidrocarburos", 2017)).thenThrow(new RuntimeException("MARCADOR_ARTIFICIAL_NO_PUBLICAR"));
        String body = mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.consultaSenace.estado").value("VACIA"))
                .andExpect(jsonPath("$.indicadorBcrp.motivo").value("ERROR_EXTERNO"))
                .andReturn().getResponse().getContentAsString();
        assertSafe(body);
    }

    @Test void normalizedProjectOptionalsAreOmittedWithoutInventingUnavailableFields() throws Exception {
        ProyectoSenaceDto minimal = new ProyectoSenaceDto(1L, null, null, "PROYECTO ARTIFICIAL", null, null,
                "Hidrocarburos", "01/01/2017", "Aprobado", null, null, null, null);
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(projects(List.of(minimal), "Hidrocarburos", 2017));
        JsonNode row = json.readTree(mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("proyectos").get(0);
        for (String absent : List.of("ruc", "latitud", "longitud", "resolucion", "inversion", "region", "moneda", "expediente")) {
            assertThat(row.has(absent)).as(absent).isFalse();
        }
    }

    @Test void consultantRowsHaveEightFieldsWithoutDeduplicationOrDerivedStatus() throws Exception {
        ConsultoraSenaceDto row = consultant();
        when(senace.consultarConsultoras()).thenReturn(consultants(List.of(row, row)));
        JsonNode result = json.readTree(mvc.perform(get("/api/consultoras"))
                .andExpect(status().isOk()).andExpect(header().string("X-Senace-Origen", "API_DIRECTA_SENACE"))
                .andExpect(header().string("X-Senace-Estado", "EXITO"))
                .andExpect(header().string("X-Senace-Cobertura", "NO_VERIFICADA"))
                .andExpect(header().string("X-Senace-Cuerpo-Completo", "true"))
                .andReturn().getResponse().getContentAsString());
        assertThat(result.isArray()).isTrue();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).size()).isEqualTo(8);
        assertThat(result.get(0).get("razonSocial").asText()).isEqualTo("CONSULTORA ARTIFICIAL");
        assertThat(result.get(0).get("actividad").asText()).isEqualTo("ACTIVIDAD ORIGINAL");
        assertThat(result.get(0).get("subsector").asText()).isEqualTo("SUBSECTOR ORIGINAL");
        assertThat(result.get(0).get("fechaRd").asText()).isEqualTo("TEXTO ORIGINAL");
        for (String absent : List.of("ruc", "registro", "nivel", "estado", "id")) assertThat(result.get(0).has(absent)).isFalse();
        verify(senace).consultarConsultoras();
        verify(senace, never()).obtenerConsultoras();
        verifyNoInteractions(bcrp);
    }

    @Test void consultantMissingFieldsAreOmittedAndEmptyIsOfficial() throws Exception {
        ConsultoraSenaceDto row = new ConsultoraSenaceDto("CONSULTORA ARTIFICIAL", "ACTIVIDAD", null, null, "", null, null, "");
        when(senace.consultarConsultoras()).thenReturn(consultants(List.of(row)));
        mvc.perform(get("/api/consultoras")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].finVigenciaRd").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].resolucion").doesNotHaveJsonPath());
        when(senace.consultarConsultoras()).thenReturn(consultants(List.of()));
        mvc.perform(get("/api/consultoras")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty()).andExpect(header().string("X-Senace-Estado", "VACIA"))
                .andExpect(header().string("X-Senace-Origen", "API_DIRECTA_SENACE"))
                .andExpect(header().string("X-Senace-Motivo", "RESPUESTA_OFICIAL_VACIA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIGURACION_AUSENTE", "ERROR_HTTP", "ERROR_TLS", "CIRCUITO_ABIERTO", "LIMITE_LECTURA"})
    void unavailableConsultantsKeepArrayAnd503Headers(String reason) throws Exception {
        EstadoConsulta estado = "LIMITE_LECTURA".equals(reason) ? EstadoConsulta.PARCIAL : EstadoConsulta.ERROR_EXTERNO;
        when(senace.consultarConsultoras()).thenReturn(failure(estado, Motivo.valueOf(reason), null));
        mvc.perform(get("/api/consultoras")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty()).andExpect(header().string("X-Senace-Origen", "NO_DISPONIBLE"))
                .andExpect(header().string("X-Senace-Estado", estado.name()))
                .andExpect(header().string("X-Senace-Motivo", reason))
                .andExpect(header().doesNotExist("X-Senace-Cuerpo-Completo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FORMATO_INVALIDO", "FILA_NULA", "ID_AUSENTE", "CODIFICACION_NO_ADMITIDA"})
    void invalidConsultantsKeepArrayAnd502Headers(String reason) throws Exception {
        when(senace.consultarConsultoras()).thenReturn(failure(EstadoConsulta.FORMATO_INVALIDO, Motivo.valueOf(reason), true));
        mvc.perform(get("/api/consultoras")).andExpect(status().isBadGateway()).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty()).andExpect(header().string("X-Senace-Estado", "FORMATO_INVALIDO"))
                .andExpect(header().string("X-Senace-Cuerpo-Completo", "true"));
    }

    @Test void consultantUnexpectedFailureIsSanitizedAs503Array() throws Exception {
        when(senace.consultarConsultoras()).thenThrow(new RuntimeException("auth_key=MARCADOR_ARTIFICIAL_NO_PUBLICAR"));
        String body = mvc.perform(get("/api/consultoras")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$").isArray()).andReturn().getResponse().getContentAsString();
        assertSafe(body);
    }

    @Test void safeClientExceptionRetainsItsMetadataAtThePublicBoundary() throws Exception {
        SenaceTransport fake = mock(SenaceTransport.class);
        when(fake.consultar(any())).thenReturn(new SenaceTransport.RespuestaTransporte(
                SenaceTransport.Estado.CONFIGURACION_AUSENTE, null,
                SenaceTransport.TipoContenido.DESCONOCIDO, null, 0, null, false, null));
        SenaceClient direct = new SenaceClient(fake, new ObjectMapper());
        Throwable safe = org.assertj.core.api.Assertions.catchThrowable(direct::consultarConsultoras);
        assertThat(safe).isInstanceOf(SenaceClient.ConsultaSenaceException.class);
        when(senace.consultarConsultoras()).thenThrow((RuntimeException) safe);
        mvc.perform(get("/api/consultoras")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$").isArray())
                .andExpect(header().string("X-Senace-Motivo", "CONFIGURACION_AUSENTE"));
    }

    @Test void partialResponseHeaderDistinguishesFalseFromUnknownCompletion() throws Exception {
        when(senace.consultarConsultoras()).thenReturn(failure(EstadoConsulta.PARCIAL, Motivo.LIMITE_LECTURA, false));
        mvc.perform(get("/api/consultoras")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$").isEmpty())
                .andExpect(header().string("X-Senace-Cuerpo-Completo", "false"));
    }

    @Test void corsExposesOnlyTheNamedMetadataWithoutChangingExistingOriginPolicy() throws Exception {
        when(senace.consultarConsultoras()).thenReturn(consultants(List.of()));
        var response = mvc.perform(get("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.invalid"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
                .andReturn().getResponse();
        String exposed = response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        assertThat(exposed).contains("X-Senace-Origen", "X-Senace-Estado", "X-Senace-Cobertura",
                "X-Senace-Motivo", "X-Senace-Cuerpo-Completo");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test void corsAlsoExposesHeadersOnDefinedErrors() throws Exception {
        when(senace.consultarConsultoras()).thenReturn(failure(EstadoConsulta.ERROR_EXTERNO, Motivo.CONFIGURACION_AUSENTE, null));
        mvc.perform(get("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.invalid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.hamcrest.Matchers.containsString("X-Senace-Motivo")));
    }

    @Test void corsPreflightAllowsGetWithoutInvokingProvidersAndDoesNotAddPost() throws Exception {
        // MVC standalone de esta version no registra el adapter para PreFlightHandler.
        // Configuracion MVC explicita, sin EnableAutoConfiguration o component scanning.
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(CorsMvc.class);
            context.addBeanFactoryPostProcessor(factory -> {
                factory.registerSingleton("senaceMock", senace);
                factory.registerSingleton("bcrpMock", bcrp);
                factory.registerSingleton("registry", registry);
            });
            context.refresh();
            assertThat(context.getBeanNamesForType(javax.sql.DataSource.class)).isEmpty();
            MockMvc cors = MockMvcBuilders.webAppContextSetup(context).build();
            cors.perform(options("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.invalid")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET"));
            cors.perform(options("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.invalid")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(senace, bcrp);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class CorsMvc {
        @Bean EcoInvestController controller(SenaceClient senace, BcrpClient bcrp, CircuitBreakerRegistry registry) {
            return new EcoInvestController(senace, bcrp, registry);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CarteraProyectos", "ConsultorasAmbientales"})
    void genericVerifiedResourcesKeepArrayAndNoAnnualFiltering(String name) throws Exception {
        Object data = "CarteraProyectos".equals(name)
                ? List.of(Map.of("ID", 1, "FECHA_INICIO", "01/01/2017"), Map.of("ID", 2, "FECHA_INICIO", "01/01/2020"))
                : List.of(Map.of("RAZON_SOCIAL", "CONSULTORA ARTIFICIAL"), Map.of("RAZON_SOCIAL", "CONSULTORA ARTIFICIAL"));
        when(senace.consultarDatastream(name)).thenReturn(generic(name, data,
                EstadoConsulta.EXITO, null, true, new Conteos(2, 2, 2, null, null, null)));
        mvc.perform(get("/api/senace/datastream").param("nombre", name)).andExpect(status().isOk())
                .andExpect(jsonPath("$.datastream").value(name)).andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.recursoValidado").value(true))
                .andExpect(jsonPath("$.consulta.estadoFiltrado").value("NO_APLICABLE"));
        verify(senace).consultarDatastream(name);
        verify(senace, never()).consultarProyectos(any(), any());
        verifyNoInteractions(bcrp);
    }

    @Test void genericDefaultAndOfficialEmptyArePreserved() throws Exception {
        when(senace.consultarDatastream("CarteraProyectos")).thenReturn(generic("CarteraProyectos", List.of(),
                EstadoConsulta.VACIA, Motivo.RESPUESTA_OFICIAL_VACIA, true, new Conteos(0, 0, 0, null, null, null)));
        mvc.perform(get("/api/senace/datastream")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty()).andExpect(jsonPath("$.origen").value("API_DIRECTA_SENACE"))
                .andExpect(jsonPath("$.consulta.estado").value("VACIA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GastoEspecifica", "GastoFuente", "GastoGenerica", "SolicitudAcceso", "Visitas", "Reclamos"})
    void genericLegacyObjectDoesNotClaimSchemaValidation(String name) throws Exception {
        when(senace.consultarDatastream(name)).thenReturn(generic(name, Map.of("campoHeredado", "FIXTURE"),
                EstadoConsulta.EXITO, null, true, Conteos.desconocidos()));
        mvc.perform(get("/api/senace/datastream").param("nombre", name)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.campoHeredado").value("FIXTURE"))
                .andExpect(jsonPath("$.recursoValidado").value(false))
                .andExpect(jsonPath("$.consulta.conteos.recibidos").doesNotHaveJsonPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIGURACION_AUSENTE", "ERROR_EXTERNO", "CIRCUITO_ABIERTO", "FORMATO_INVALIDO", "RESPUESTA_PARCIAL"})
    void genericErrorsPreserveEnvelopeAndDiscardExternalBody(String reason) throws Exception {
        EstadoConsulta state = "FORMATO_INVALIDO".equals(reason) ? EstadoConsulta.FORMATO_INVALIDO
                : "RESPUESTA_PARCIAL".equals(reason) ? EstadoConsulta.PARCIAL : EstadoConsulta.ERROR_EXTERNO;
        Map<String, Object> raw = generic("CarteraProyectos", "auth_key=MARCADOR_ARTIFICIAL_NO_PUBLICAR",
                state, Motivo.valueOf(reason), false, Conteos.desconocidos());
        raw.put("mensaje", "MARCADOR_ARTIFICIAL_NO_PUBLICAR");
        when(senace.consultarDatastream("CarteraProyectos")).thenReturn(raw);
        var result = mvc.perform(get("/api/senace/datastream"))
                .andExpect(status().is("FORMATO_INVALIDO".equals(reason) ? 502 : 503))
                .andExpect(jsonPath("$.origen").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.consulta.motivo").value(reason)).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("data")).isTrue();
        assertThat(body.get("data").isNull()).isTrue();
        assertSafe(result.getResponse().getContentAsString());
    }

    @Test void genericMalformedMetadataDoesNotReflectUnsafeReason() throws Exception {
        var raw = generic("Visitas", Map.of(), EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_EXTERNO, null, Conteos.desconocidos());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) raw.get("consulta");
        meta.put("motivo", "https://fixture.invalid?auth_key=MARCADOR_ARTIFICIAL_NO_PUBLICAR");
        when(senace.consultarDatastream("Visitas")).thenReturn(raw);
        String body = mvc.perform(get("/api/senace/datastream").param("nombre", "Visitas"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.consulta.motivo").value("FORMATO_INVALIDO"))
                .andReturn().getResponse().getContentAsString();
        assertSafe(body);
    }

    @Test void genericWrongRootDoesNotBecomeAnOfficialEmptyResult() throws Exception {
        when(senace.consultarDatastream("CarteraProyectos")).thenReturn(generic("CarteraProyectos", Map.of(),
                EstadoConsulta.EXITO, null, true, Conteos.desconocidos()));
        mvc.perform(get("/api/senace/datastream")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.consulta.estado").value("FORMATO_INVALIDO"));
    }

    @Test void genericUnexpectedExceptionAndUnknownInputAreSanitized() throws Exception {
        String input = "https://fixture.invalid?auth_key=MARCADOR_ARTIFICIAL_NO_PUBLICAR";
        when(senace.consultarDatastream(input)).thenThrow(new RuntimeException(input));
        String body = mvc.perform(get("/api/senace/datastream").param("nombre", input))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.datastream").value("NO_RECONOCIDO"))
                .andReturn().getResponse().getContentAsString();
        assertSafe(body);
    }

    @Test void monitorOnlyReadsLogicalMetricsAndDoesNotConsultProviders() throws Exception {
        registry.circuitBreaker("senace").onError(1, java.util.concurrent.TimeUnit.MILLISECONDS,
                new IllegalStateException("FALLO LOGICO ARTIFICIAL"));
        mvc.perform(get("/api/estado-servicios")).andExpect(status().isOk())
                .andExpect(jsonPath("$.senace.llamadasFallidas").value(1))
                .andExpect(jsonPath("$.senace.estado").value("CLOSED"))
                .andExpect(jsonPath("$.senace.alcanceMetricas").value("LLAMADAS_LOGICAS"))
                .andExpect(jsonPath("$.senace.disponibilidadExterna").value("NO_VERIFICADA"))
                .andExpect(jsonPath("$.senace.respaldoLocal").value("NO_IMPLEMENTADO"))
                .andExpect(jsonPath("$.bcrp.respaldoLocal").value("NO_IMPLEMENTADO"));
        verifyNoInteractions(senace, bcrp);
    }

    @Test void sectorRemainsRequiredAndYearDefaultRemains2017() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isBadRequest());
        verifyNoInteractions(senace, bcrp);
        when(senace.consultarProyectos("Hidrocarburos", 2017)).thenReturn(projects(List.of(), "Hidrocarburos", 2017));
        mvc.perform(get("/api/dashboard").param("sector", "Hidrocarburos")).andExpect(status().isOk());
        verify(senace).consultarProyectos("Hidrocarburos", 2017);
        verify(bcrp).obtenerDatosBcrp("Hidrocarburos", 2017);
    }

    private ResultadoConsultaSenace<ProyectoMapeado> projects(List<ProyectoSenaceDto> rows, String sector, Integer year) {
        return complete(mapper.mapearProyectos(rows, sector, year));
    }

    private ResultadoConsultaSenace<ConsultoraMapeada> consultants(List<ConsultoraSenaceDto> rows) {
        return complete(mapper.mapearConsultoras(rows));
    }

    private static <T> ResultadoConsultaSenace<T> complete(ResultadoConsultaSenace<T> mapped) {
        // Evidencia HTTP artificial del fixture; no acredita una consulta real.
        return new ResultadoConsultaSenace<>(mapped.registros(), mapped.origen(), mapped.estadoConsulta(),
                mapped.estadoFiltrado(), mapped.cobertura(), true, mapped.motivo(), mapped.conteos(), mapped.advertencias());
    }

    private static <T> ResultadoConsultaSenace<T> failure(EstadoConsulta state, Motivo motivo, Boolean completo) {
        return new ResultadoConsultaSenace<>(List.of(), Origen.NO_DISPONIBLE, state, EstadoFiltrado.NO_APLICABLE,
                ResultadoConsultaSenace.Cobertura.NO_VERIFICADA, completo, motivo, Conteos.desconocidos(), List.of());
    }

    private static ProyectoSenaceDto project(long id, String actividad, String fecha, String estado) {
        return new ProyectoSenaceDto(id, "TITULAR ARTIFICIAL", "00000000001", "PROYECTO ARTIFICIAL", "UNIDAD ARTIFICIAL",
                "TIPO ARTIFICIAL", actividad, fecha, estado, "DESCRIPCION ARTIFICIAL",
                new BigDecimal("-70.12345678901234567890"), new BigDecimal("-12.12345678901234567890"), "RESOLUCION ARTIFICIAL");
    }

    private static ConsultoraSenaceDto consultant() {
        return new ConsultoraSenaceDto("CONSULTORA ARTIFICIAL", "ACTIVIDAD ORIGINAL", "SUBSECTOR ORIGINAL",
                "TIPO ORIGINAL", "RESOLUCION ORIGINAL", "TEXTO ORIGINAL", "INICIO ORIGINAL", "FIN ORIGINAL");
    }

    private static Map<String, Object> generic(String name, Object data, EstadoConsulta state, Motivo motivo,
            Boolean completo, Conteos conteos) {
        boolean official = state == EstadoConsulta.EXITO || state == EstadoConsulta.VACIA;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("estado", state.name());
        meta.put("origen", official ? "API_DIRECTA_SENACE" : "NO_DISPONIBLE");
        meta.put("estadoFiltrado", "NO_APLICABLE");
        meta.put("cobertura", "NO_VERIFICADA");
        if (completo != null) meta.put("cuerpoCompleto", completo);
        if (motivo != null) meta.put("motivo", motivo.name());
        meta.put("conteos", conteos);
        meta.put("advertencias", List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datastream", name);
        result.put("origen", meta.get("origen"));
        result.put("data", data);
        result.put("consulta", meta);
        return result;
    }

    private static void assertSafe(String body) {
        assertThat(body).doesNotContain("MARCADOR_ARTIFICIAL_NO_PUBLICAR", "auth_key", "fixture.invalid", "stackTrace", "cause");
    }

    private static Map<String, Object> economicData(String code, String sector, String scope, int year, BigDecimal value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("codigoSerie", code);
        data.put("descripcion", "Producto bruto interno por sectores productivos (variación porcentual interanual) - " + sector);
        data.put("sectorEconomico", sector);
        data.put("unidad", "Variación porcentual interanual (%)");
        data.put("periodicidad", "ANUAL");
        data.put("ambito", scope);
        data.put("anioSolicitado", year);
        data.put("periodo", value == null ? null : String.valueOf(year));
        data.put("valor", value);
        data.put("origen", value == null ? "NO_DISPONIBLE" : "API_DIRECTA_BCRP");
        data.put("urlFuente", "https://estadisticas.bcrp.gob.pe/estadisticas/series/api/" + code + "/json/" + year + "/" + year + "/esp");
        return data;
    }
}
