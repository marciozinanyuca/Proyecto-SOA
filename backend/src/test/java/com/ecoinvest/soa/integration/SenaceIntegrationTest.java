package com.ecoinvest.soa.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.SenaceClient;
import com.ecoinvest.soa.client.SenaceTransport;
import com.ecoinvest.soa.client.SenaceTransport.Estado;
import com.ecoinvest.soa.client.SenaceTransport.Recurso;
import com.ecoinvest.soa.client.SenaceTransport.RespuestaTransporte;
import com.ecoinvest.soa.client.SenaceTransport.TipoContenido;
import com.ecoinvest.soa.config.AppConfig;
import com.ecoinvest.soa.controller.EcoInvestController;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

/**
 * Integracion MVC/Jackson/AOP con cliente, DTOs y mapper reales.
 * Todos los cuerpos son artificiales; API_DIRECTA describe el escenario simulado,
 * no una consulta al proveedor. No usa @SpringBootTest, escaneo, sockets o SQLite.
 */
class SenaceIntegrationTest {
    private static final String PROJECTS = "[" + project("9007199254740993", "  Minería  ", "05/04/2017", "Aprobado")
            + "," + project("2", "MINERIA", "06/04/2017", "En Evaluación")
            + "," + project("3", "Minería", "01/01/2018", "Aprobado")
            + "," + project("4", "Pesca", "01/01/2017", "Aprobado") + "]";
    private static final String CONSULTANT = """
            {"RAZON_SOCIAL":"CONSULTORA ARTIFICIAL","ACTIVIDAD":"ACTIVIDAD ORIGINAL",
             "SUBSECTOR":"SUBSECTOR ORIGINAL","TIPO_SOLICITUD":"TIPO ORIGINAL",
             "RESOLUCION":"RESOLUCION ARTIFICIAL","FECHA_RD":"TEXTO ORIGINAL",
             "INICIO_VIGENCIA_RD":"INICIO ORIGINAL","FIN_VIGENCIA_RD":"FIN ORIGINAL"}
            """;

    @Test void projectsTraverseMvcClientDtoMapperAndGeneratedModel() {
        scenario(f -> {
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
            JsonNode body = f.body(f.mvc.perform(get("/api/dashboard").param("sector", "MINERIA"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.aprobados").value(1))
                    .andExpect(jsonPath("$.proyectos[0].ruc").value("00000000001"))
                    .andExpect(jsonPath("$.proyectos[0].actividad").value("  Minería  "))
                    .andExpect(jsonPath("$.proyectos[0].fechaInicio").value("05/04/2017"))
                    .andExpect(jsonPath("$.proyectos[0].anio").value(2017))
                    .andExpect(jsonPath("$.consultaSenace.conteos.recibidos").value(4))
                    .andExpect(jsonPath("$.consultaSenace.conteos.devueltos").value(2))
                    .andExpect(jsonPath("$.consultaSenace.cuerpoCompleto").value(true))
                    .andExpect(jsonPath("$.consultaSenace.cobertura").value("NO_VERIFICADA"))
                    .andExpect(jsonPath("$.consultaSenace.motivo").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$.fuentes.bcrp").value("API_DIRECTA_BCRP"))
                    .andReturn());
            assertThat(body.at("/proyectos/0/id").longValue()).isEqualTo(9007199254740993L);
            assertThat(body.at("/proyectos/0/longitud").decimalValue()).isEqualByComparingTo("-70.12345678901234567890");
            assertThat(body.at("/proyectos/0/latitud").decimalValue()).isEqualByComparingTo("-12.12345678901234567890");
            assertThat(body.at("/variacionSeleccionada").decimalValue())
                    .isEqualByComparingTo(body.at("/indicadorBcrp/valor").decimalValue());
            for (String absent : new String[]{"inversion", "moneda", "region", "expediente"}) {
                assertThat(body.at("/proyectos/0").has(absent)).isFalse();
            }
            verify(f.transport).consultar(Recurso.CARTERA_PROYECTOS);
            verify(f.bcrp).obtenerDatosBcrp("MINERIA", 2017);
            assertThat(f.breaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        });
    }

    @Test void selectedYearIsStartYearAndNotApprovalYear() {
        scenario(f -> {
            when(f.transport.consultar(any())).thenReturn(received(PROJECTS));
            f.mvc.perform(get("/api/dashboard").param("sector", "minería").param("anio", "2018"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.proyectos[0].id").value(3))
                    .andExpect(jsonPath("$.proyectos[0].fechaInicio").value("01/01/2018"));
            verify(f.bcrp).obtenerDatosBcrp("minería", 2018);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "noMatches", "invalidDate", "missingDate"})
    void emptySelectionsAndTemporalQualityRemainDistinct(String kind) {
        scenario(f -> {
            String data = switch (kind) {
                case "empty" -> "[]";
                case "noMatches" -> "[" + project("1", "Pesca", "01/01/2017", "Aprobado") + "]";
                case "invalidDate" -> "[" + project("1", "Minería", "31/02/2017", "Aprobado") + "]";
                default -> "[{\"ID\":1,\"ACTIVIDAD\":\"Minería\"}]";
            };
            when(f.transport.consultar(any())).thenReturn(received(data));
            var result = f.mvc.perform(get("/api/dashboard").param("sector", "MINERIA"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.aprobados").value(0))
                    .andExpect(jsonPath("$.fuentes.senace").value("API_DIRECTA_SENACE"))
                    .andExpect(jsonPath("$.indicadorBcrp.origen").value("API_DIRECTA_BCRP"));
            if (kind.equals("empty")) {
                result.andExpect(jsonPath("$.consultaSenace.estado").value("VACIA"))
                        .andExpect(jsonPath("$.consultaSenace.motivo").value("RESPUESTA_OFICIAL_VACIA"));
            } else if (kind.equals("noMatches")) {
                result.andExpect(jsonPath("$.consultaSenace.estado").value("EXITO"))
                        .andExpect(jsonPath("$.consultaSenace.estadoFiltrado").value("SIN_COINCIDENCIAS"));
            } else {
                result.andExpect(jsonPath("$.consultaSenace.estadoFiltrado").value("NO_EVALUABLE"))
                        .andExpect(jsonPath("$.consultaSenace.conteos.fechasNoEvaluables").value(1))
                        .andExpect(jsonPath("$.consultaSenace.conteos.fechasNoEvaluablesEnActividad").value(1))
                        .andExpect(jsonPath("$.consultaSenace.advertencias[0]")
                                .value(kind.equals("invalidDate") ? "FECHA_INVALIDA" : "FECHA_AUSENTE"));
            }
            verify(f.bcrp).obtenerDatosBcrp("MINERIA", 2017);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"badId", "nullRow", "badJson", "wrongRoot", "partial", "external", "config"})
    void realFallbackPreservesBcrpWhenSenaceCannotBeUsed(String kind) {
        scenario(f -> {
            RespuestaTransporte response = switch (kind) {
                case "badId" -> received("[{\"ID\":1.5}]");
                case "nullRow" -> received("[null]");
                case "badJson" -> received("[{incorrecto]");
                case "wrongRoot" -> received("{}");
                // Documento interpretable pero evidencia de transporte incompleto: no aceptarlo.
                case "partial" -> new RespuestaTransporte(Estado.RECIBIDO, 200, TipoContenido.JSON,
                        "application/json", 2, 10L, false, "[]");
                case "config" -> failed(Estado.CONFIGURACION_AUSENTE);
                default -> failed(Estado.ERROR_HTTP);
            };
            when(f.transport.consultar(any())).thenReturn(response);
            var result = f.mvc.perform(get("/api/dashboard").param("sector", "MINERIA"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$.aprobados").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$.proyectos").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$.fuentes.senace").value("NO_DISPONIBLE"))
                    .andExpect(jsonPath("$.indicadorBcrp.origen").value("API_DIRECTA_BCRP"))
                    .andExpect(jsonPath("$.consultaSenace.motivo").isString());
            String state = switch (kind) {
                case "partial" -> "PARCIAL";
                case "external", "config" -> "ERROR_EXTERNO";
                default -> "FORMATO_INVALIDO";
            };
            result.andExpect(jsonPath("$.consultaSenace.estado").value(state));
            if (kind.equals("config")) result.andExpect(jsonPath("$.consultaSenace.motivo").value("CONFIGURACION_AUSENTE"));
            if (kind.equals("partial")) result.andExpect(jsonPath("$.consultaSenace.cuerpoCompleto").value(false));
            verify(f.bcrp).obtenerDatosBcrp("MINERIA", 2017);
            assertThat(f.breaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        });
    }

    @Test void missingEconomicIndicatorDoesNotInventValueOrRemoveSenace() {
        scenario(f -> {
            when(f.transport.consultar(any())).thenReturn(received(PROJECTS));
            when(f.bcrp.obtenerDatosBcrp(anyString(), anyInt())).thenReturn(Map.of());
            f.mvc.perform(get("/api/dashboard").param("sector", "MINERIA"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.variacionSeleccionada").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$.indicadorBcrp.origen").value("NO_DISPONIBLE"));
        });
    }

    @Test void consultantsKeepEightOriginalFieldsDuplicatesAndNoInventedIdentity() {
        scenario(f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES))
                    .thenReturn(received("[" + CONSULTANT + "," + CONSULTANT + ",{}]"));
            JsonNode body = f.body(f.mvc.perform(get("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.example"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                    .andExpect(header().string("X-Senace-Origen", "API_DIRECTA_SENACE"))
                    .andExpect(header().string("X-Senace-Estado", "EXITO"))
                    .andExpect(header().string("X-Senace-Cobertura", "NO_VERIFICADA"))
                    .andExpect(header().string("X-Senace-Cuerpo-Completo", "true"))
                    .andReturn());
            assertThat(body.isArray()).isTrue();
            assertThat(body.get(0)).isEqualTo(body.get(1));
            Map<String, String> fields = Map.of("razonSocial", "CONSULTORA ARTIFICIAL", "actividad", "ACTIVIDAD ORIGINAL",
                    "subsector", "SUBSECTOR ORIGINAL", "tipoSolicitud", "TIPO ORIGINAL", "resolucion", "RESOLUCION ARTIFICIAL",
                    "fechaRd", "TEXTO ORIGINAL", "inicioVigenciaRd", "INICIO ORIGINAL", "finVigenciaRd", "FIN ORIGINAL");
            assertThat(body.get(0).size()).isEqualTo(8);
            fields.forEach((key, value) -> assertThat(body.get(0).get(key).asText()).isEqualTo(value));
            for (String absent : new String[]{"ruc", "id", "registro", "nivel", "estado", "vigencia"}) {
                assertThat(body.get(0).has(absent)).isFalse();
            }
            assertThat(body.get(2).size()).isZero();
            verify(f.transport).consultar(Recurso.CONSULTORAS_AMBIENTALES);
            verifyNoInteractions(f.bcrp);
        });
    }

    @Test void blankConsultantStringsAreOmittedAndDatesAreNotInterpreted() {
        scenario(f -> {
            when(f.transport.consultar(any())).thenReturn(received("[{\"RAZON_SOCIAL\":\"  \",\"FECHA_RD\":\"texto sin fecha\"}]"));
            f.mvc.perform(get("/api/consultoras")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].razonSocial").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$[0].fechaRd").value("texto sin fecha"))
                    .andExpect(jsonPath("$[0].estado").doesNotHaveJsonPath());
        });
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "external", "config", "invalid", "html", "partial"})
    void consultantStatusCodesAndArrayRootFollowActualClientResults(String kind) {
        scenario(f -> {
            RespuestaTransporte response = switch (kind) {
                case "empty" -> received("[]");
                case "invalid" -> received("[1]");
                case "html" -> failed(Estado.HTML_INESPERADO);
                case "partial" -> failed(Estado.RESPUESTA_INCOMPLETA);
                case "config" -> failed(Estado.CONFIGURACION_AUSENTE);
                default -> failed(Estado.ERROR_HTTP);
            };
            when(f.transport.consultar(any())).thenReturn(response);
            int code = kind.equals("empty") ? 200 : kind.equals("invalid") || kind.equals("html") ? 502 : 503;
            f.mvc.perform(get("/api/consultoras")).andExpect(status().is(code))
                    .andExpect(content().json("[]"))
                    .andExpect(header().string("X-Senace-Origen", kind.equals("empty") ? "API_DIRECTA_SENACE" : "NO_DISPONIBLE"))
                    .andExpect(header().string("X-Senace-Cobertura", "NO_VERIFICADA"))
                    .andExpect(header().exists("X-Senace-Estado"));
            verifyNoInteractions(f.bcrp);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"CarteraProyectos", "ConsultorasAmbientales", "Visitas"})
    void genericJsonNullableUsesMvcObjectMapperForArraysAndObjects(String name) {
        scenario(f -> {
            boolean legacy = name.equals("Visitas");
            String data = legacy ? "{\"datos\":[{\"valor\":0.12345678901234567890}]}"
                    : name.equals("CarteraProyectos") ? "[{\"ID\":1,\"FECHA_INICIO\":\"01/01/2018\"},{\"ID\":2}]"
                    : "[" + CONSULTANT + "," + CONSULTANT + "]";
            when(f.transport.consultar(any())).thenReturn(received(data));
            JsonNode body = f.body(f.mvc.perform(get("/api/senace/datastream").param("nombre", name))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.datastream").value(name))
                    .andExpect(jsonPath("$.recursoValidado").value(!legacy))
                    .andExpect(jsonPath("$.consulta.estadoFiltrado").value("NO_APLICABLE"))
                    .andExpect(jsonPath("$.consulta.cuerpoCompleto").value(true)).andReturn());
            assertThat(body.get("data").has("present")).isFalse();
            if (legacy) {
                assertThat(body.get("data").isObject()).isTrue();
                assertThat(body.at("/data/datos/0/valor").decimalValue()).isEqualByComparingTo("0.12345678901234567890");
            } else {
                assertThat(body.get("data").isArray()).isTrue();
                assertThat(body.get("data").size()).isEqualTo(2);
                if (name.equals("CarteraProyectos")) assertThat(body.at("/data/0/FECHA_INICIO").asText()).isEqualTo("01/01/2018");
            }
            verify(f.transport).consultar(name.equals("CarteraProyectos") ? Recurso.CARTERA_PROYECTOS
                    : legacy ? Recurso.VISITAS : Recurso.CONSULTORAS_AMBIENTALES);
            verifyNoInteractions(f.bcrp);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "invalid", "config", "external"})
    void genericEmptyArrayAndExplicitNullErrorsRemainDifferent(String kind) {
        scenario(f -> {
            when(f.transport.consultar(any())).thenReturn(switch (kind) {
                case "empty" -> received("[]");
                case "invalid" -> received("{}");
                case "config" -> failed(Estado.CONFIGURACION_AUSENTE);
                default -> failed(Estado.ERROR_HTTP);
            });
            JsonNode body = f.body(f.mvc.perform(get("/api/senace/datastream").param("nombre", "CarteraProyectos"))
                    .andExpect(status().is(kind.equals("empty") ? 200 : kind.equals("invalid") ? 502 : 503)).andReturn());
            assertThat(body.has("data")).isTrue();
            if (kind.equals("empty")) {
                assertThat(body.get("data").isArray()).isTrue();
                assertThat(body.get("data").size()).isZero();
                assertThat(body.at("/consulta/estado").asText()).isEqualTo("VACIA");
            } else assertThat(body.get("data").isNull()).isTrue();
        });
    }

    @Test void circuitOpensFromActualFailuresAndDashboardStillConsultsBcrp() {
        scenario(f -> {
            when(f.transport.consultar(any())).thenReturn(failed(Estado.ERROR_HTTP));
            CircuitBreaker breaker = f.breaker();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            int threshold = Math.min(breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                    breaker.getCircuitBreakerConfig().getSlidingWindowSize());
            for (int i = 0; i < threshold; i++) {
                f.mvc.perform(get("/api/dashboard").param("sector", "MINERIA"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.consultaSenace.motivo").value("ERROR_HTTP"));
            }
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            f.mvc.perform(get("/api/dashboard").param("sector", "MINERIA"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.consultaSenace.motivo").value("CIRCUITO_ABIERTO"))
                    .andExpect(jsonPath("$.indicadorBcrp.origen").value("API_DIRECTA_BCRP"));
            verify(f.transport, times(threshold)).consultar(Recurso.CARTERA_PROYECTOS);
            verify(f.bcrp, times(threshold + 1)).obtenerDatosBcrp("MINERIA", 2017);
            assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
        });
    }

    @Test void unsafeExternalExceptionIsNotPublishedByRealFallbackOrMvc() {
        scenario(f -> {
            when(f.transport.consultar(any())).thenThrow(new IllegalStateException(
                    "https://fixture.invalid?auth_key=MARCADOR_ARTIFICIAL_NO_PUBLICAR"));
            for (String route : new String[]{"/api/dashboard?sector=MINERIA", "/api/consultoras", "/api/senace/datastream"}) {
                MvcResult result = f.mvc.perform(get(route)).andReturn();
                assertThat(result.getResponse().getStatus()).isEqualTo(route.contains("dashboard") ? 200 : 503);
                assertThat(result.getResponse().getContentAsString()).doesNotContain("auth_key", "MARCADOR_ARTIFICIAL",
                        "fixture.invalid", "stackTrace", "cause");
                for (String header : result.getResponse().getHeaderNames()) {
                    assertThat(result.getResponse().getHeader(header)).doesNotContain("auth_key", "MARCADOR_ARTIFICIAL", "fixture.invalid");
                }
            }
        });
    }

    @Test void monitorAndCorsUseMvcWithoutProviderCalls() {
        scenario(f -> {
            f.mvc.perform(get("/api/estado-servicios")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.senace.disponibilidadExterna").value("NO_VERIFICADA"))
                    .andExpect(jsonPath("$.senace.respaldoLocal").value("NO_IMPLEMENTADO"));
            f.mvc.perform(options("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
            f.mvc.perform(options("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")).andExpect(status().isForbidden());
            verifyNoInteractions(f.transport, f.bcrp);
            when(f.transport.consultar(any())).thenReturn(received("[]"));
            String exposed = f.mvc.perform(get("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.example"))
                    .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
            assertThat(exposed).contains("X-Senace-Origen", "X-Senace-Estado", "X-Senace-Cobertura");
        });
    }

    private static WebApplicationContextRunner context() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class,
                        CircuitBreakerAutoConfiguration.class))
                .withUserConfiguration(Beans.class)
                .withInitializer(context -> {
                    try {
                        Map<String, Object> policy = new LinkedHashMap<>();
                        for (var source : new YamlPropertySourceLoader().load("policy-only", new ClassPathResource("application.yml"))) {
                            if (source instanceof EnumerablePropertySource<?> enumerable) {
                                for (String name : enumerable.getPropertyNames()) {
                                    if (name.startsWith("resilience4j.")) policy.put(name, source.getProperty(name));
                                }
                            }
                        }
                        if (policy.isEmpty()) throw new IllegalStateException("POLITICA_RESILIENCE_AUSENTE");
                        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("actual-breaker-policy", policy));
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("POLITICA_PRUEBA_NO_DISPONIBLE");
                    }
                });
    }

    private static void scenario(Scenario test) {
        context().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.getBeansOfType(EntityManager.class)).isEmpty();
            assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(ServletWebServerFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(RestTemplate.class)).isEmpty();
            SenaceClient client = context.getBean(SenaceClient.class);
            assertThat(AopUtils.isAopProxy(client)).isTrue();
            assertThat(mockingDetails(client).isMock()).isFalse();
            assertThat(mockingDetails(context.getBean(EcoInvestController.class)).isMock()).isFalse();
            SenaceTransport transport = context.getBean(SenaceTransport.class);
            BcrpClient bcrp = context.getBean(BcrpClient.class);
            assertThat(mockingDetails(transport).isMock()).isTrue();
            assertThat(mockingDetails(bcrp).isMock()).isTrue();
            when(bcrp.obtenerDatosBcrp(anyString(), anyInt())).thenAnswer(invocation -> economic(invocation.getArgument(1)));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                    .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).build();
            test.run(new Fixture(mvc, transport, bcrp, context.getBean(CircuitBreakerRegistry.class),
                    context.getBean(ObjectMapper.class).copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)));
        });
    }

    @FunctionalInterface private interface Scenario { void run(Fixture fixture) throws Exception; }

    private record Fixture(MockMvc mvc, SenaceTransport transport, BcrpClient bcrp,
            CircuitBreakerRegistry registry, ObjectMapper reader) {
        CircuitBreaker breaker() { return registry.circuitBreaker("senace"); }
        JsonNode body(MvcResult result) throws Exception { return reader.readTree(result.getResponse().getContentAsString()); }
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean SenaceTransport transport() { return mock(SenaceTransport.class); }
        @Bean BcrpClient bcrp() { return mock(BcrpClient.class); }
        // Reutiliza la fabrica productiva, sin importar el RestTemplate de AppConfig.
        @Bean JsonNullableModule nullableModule() { return new AppConfig().jsonNullableModule(); }
        @Bean SenaceClient senace(SenaceTransport transport, ObjectMapper jackson) { return new SenaceClient(transport, jackson); }
        @Bean EcoInvestController controller(SenaceClient senace, BcrpClient bcrp, CircuitBreakerRegistry registry) {
            return new EcoInvestController(senace, bcrp, registry);
        }
    }

    private static RespuestaTransporte received(String json) {
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        return new RespuestaTransporte(Estado.RECIBIDO, 200, TipoContenido.JSON, "application/json", bytes, (long) bytes, true, json);
    }

    private static RespuestaTransporte failed(Estado state) {
        boolean noHttp = state == Estado.CONFIGURACION_AUSENTE;
        return new RespuestaTransporte(state, noHttp ? null : 503, TipoContenido.DESCONOCIDO, null,
                0, null, false, null);
    }

    private static Map<String, Object> economic(int year) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("codigoSerie", "PM04973AA");
        data.put("descripcion", "INDICADOR ARTIFICIAL PARA PRUEBA");
        data.put("sectorEconomico", "Minería metálica");
        data.put("unidad", "Variación porcentual interanual (%)");
        data.put("periodicidad", "ANUAL");
        data.put("ambito", "SECTORIAL");
        data.put("anioSolicitado", year);
        data.put("periodo", Integer.toString(year));
        data.put("valor", new BigDecimal("1.234567890123456789"));
        data.put("origen", "API_DIRECTA_BCRP");
        return data;
    }

    private static String project(String id, String activity, String date, String state) {
        return """
                {"ID":%s,"TITULAR":"TITULAR ARTIFICIAL","RUC":"00000000001",
                 "TITULO_PROYECTO":"PROYECTO ARTIFICIAL","UNIDAD_PROYECTO":"UNIDAD ARTIFICIAL",
                 "TIPO":"TIPO ARTIFICIAL","ACTIVIDAD":"%s","FECHA_INICIO":"%s",
                 "ESTADO":"%s","DESCRIPCION":"DESCRIPCION ARTIFICIAL",
                 "LONGITUD":-70.12345678901234567890,"LATITUD":-12.12345678901234567890,
                 "RESOLUCION":"RESOLUCION ARTIFICIAL"}
                """.formatted(id, activity, date, state);
    }
}
