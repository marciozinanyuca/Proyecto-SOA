package com.ecoinvest.soa.integration;

import com.ecoinvest.soa.client.*;
import com.ecoinvest.soa.config.AppConfig;
import com.ecoinvest.soa.controller.EcoInvestController;
import com.ecoinvest.soa.snapshot.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import static com.ecoinvest.soa.client.SenaceTransport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Flujo MVC/contrato/Jackson/AOP real; bordes externos artificiales, temporales y ningun socket. */
class LkgPublicIntegrationTest {
    @TempDir Path temporary;
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final String BASE = "https://bcrp.invalid/api";
    private static final String PROJECTS = """
            [{"ID":9007199254740993,"TITULAR":"TITULAR ARTIFICIAL","RUC":"00000000001",
              "TITULO_PROYECTO":"PROYECTO ARTIFICIAL","UNIDAD_PROYECTO":"UNIDAD ARTIFICIAL",
              "TIPO":"TIPO ARTIFICIAL","ACTIVIDAD":"MINERIA","FECHA_INICIO":"01/02/2017",
              "ESTADO":"Aprobado","DESCRIPCION":"PRUEBA","LONGITUD":-77.1234567890123456789,
              "LATITUD":-12.1,"RESOLUCION":"ARTIFICIAL"},
             {"ID":2,"ACTIVIDAD":"PESCA","FECHA_INICIO":"01/01/2018"}]
            """;
    private static final String CONSULTANT = """
            {"RAZON_SOCIAL":"CONSULTORA ARTIFICIAL","ACTIVIDAD":"ACTIVIDAD ARTIFICIAL",
             "SUBSECTOR":"SUBSECTOR ARTIFICIAL","TIPO_SOLICITUD":"SOLICITUD ARTIFICIAL",
             "RESOLUCION":"RESOLUCION ARTIFICIAL","FECHA_RD":"TEXTO ORIGINAL",
             "INICIO_VIGENCIA_RD":"INICIO ORIGINAL","FIN_VIGENCIA_RD":"FIN ORIGINAL"}
            """;
    private static final String ECONOMIC = """
            {"config":{"series":[{"name":"Producto bruto interno por sectores productivos (variación porcentual interanual) - Minería e Hidrocarburos - Minería Metálica"}]},
             "periods":[{"name":"2017","values":["4.1234567890123456789"]}]}
            """;

    private WebApplicationContextRunner context(boolean enabled, Path directory) {
        return new WebApplicationContextRunner()
                .withPropertyValues("spring.config.location=classpath:/application.yml", "spring.config.additional-location=",
                        "spring.config.import=", "spring.profiles.include=", "spring.profiles.active=integracion-local",
                        "external.apis.bcrp-base-url=" + BASE,
                        "ecoinvest.snapshots.enabled=" + enabled, "ECOINVEST_SNAPSHOT_DIR=" + directory,
                        "ecoinvest.snapshots.senace-stale-after=PT24H", "ecoinvest.snapshots.senace-max-age=P90D",
                        "ecoinvest.snapshots.bcrp-stale-after=P7D", "ecoinvest.snapshots.bcrp-max-age=P365D")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class, CircuitBreakerAutoConfiguration.class))
                .withUserConfiguration(Beans.class, SnapshotConfiguration.class);
    }

    @FunctionalInterface private interface Scenario { void run(Fixture fixture) throws Exception; }

    private void scenario(boolean enabled, Scenario test) {
        context(enabled, temporary.resolve("store")).run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertArrayEquals(new String[] {"integracion-local"}, ctx.getEnvironment().getActiveProfiles());
            assertEquals("127.0.0.1", ctx.getEnvironment().getProperty("server.address"));
            assertEquals("8081", ctx.getEnvironment().getProperty("server.port"));
            for (int i = 0; i < 3; i++) assertNotNull(ctx.getEnvironment().getProperty("spring.autoconfigure.exclude[" + i + "]"));
            assertTrue(ctx.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(ctx.getBeansOfType(EntityManager.class).isEmpty());
            assertTrue(ctx.getBeansOfType(EntityManagerFactory.class).isEmpty());
            assertTrue(ctx.getBeansOfType(ServletWebServerFactory.class).isEmpty());
            assertTrue(AopUtils.isAopProxy(ctx.getBean(SenaceClient.class)));
            assertTrue(AopUtils.isAopProxy(ctx.getBean(BcrpClient.class)));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx.getSourceApplicationContext())
                    .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).build();
            var fixture = new Fixture(mvc, ctx.getBean(SenaceTransport.class), ctx.getBean(HttpFixture.class),
                    ctx.getBean(ObjectMapper.class), ctx.getBean(TestClock.class),
                    ctx.getBean(CircuitBreakerRegistry.class));
            test.run(fixture);
            fixture.http.server.verify();
        });
    }

    @Test void disabledModePreservesHistoricalHeadersRootsAndMonitor() {
        scenario(false, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(received("[]"));
            f.get("/api/consultoras").andExpect(status().isOk()).andExpect(content().json("[]"))
                    .andExpect(header().string("X-Senace-Origen", "API_DIRECTA_SENACE"))
                    .andExpect(header().doesNotExist("X-Senace-Intento-Estado"));
            f.get("/api/estado-servicios").andExpect(status().isOk())
                    .andExpect(jsonPath("$.senace.respaldoLocal").value("NO_IMPLEMENTADO"))
                    .andExpect(jsonPath("$.lkg").doesNotExist());
            assertFalse(Files.exists(temporary.resolve("store")));
        });
    }

    @Test void enabledButUnverifiedMonitorDoesNotReadDiskOrCallProviders() {
        scenario(true, f -> {
            f.get("/api/estado-servicios").andExpect(status().isOk())
                    .andExpect(jsonPath("$.lkg.estado").value("HABILITADO_SIN_VERIFICACION"))
                    .andExpect(jsonPath("$.lkg.observaciones").isEmpty())
                    .andExpect(jsonPath("$.senace.disponibilidadExterna").value("NO_VERIFICADA"));
            verifyNoInteractions(f.transport);
            assertFalse(Files.exists(temporary.resolve("store")));
        });
    }

    @Test void directDashboardUpdatesSnapshotAndKeepsContractPrecisionAndOptionalFields() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
            f.economic(2017, true);
            JsonNode response = f.body(f.dashboard(2017).andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.aprobados").value(1))
                    .andExpect(jsonPath("$.consultaSenace.origen").value("API_DIRECTA_SENACE"))
                    .andExpect(jsonPath("$.consultaSenace.lkg.escritura").value("SAVED"))
                    .andExpect(jsonPath("$.indicadorBcrp.lkg.escritura").value("SAVED"))
                    .andExpect(jsonPath("$.proyectos[0].ruc").value("00000000001"))
                    .andExpect(jsonPath("$.proyectos[0].inversion").doesNotExist()).andReturn());
            assertEquals(9007199254740993L, response.path("proyectos").get(0).path("id").longValue());
            assertEquals(new java.math.BigDecimal("-77.1234567890123456789"),
                    response.path("proyectos").get(0).path("longitud").decimalValue());
            assertEquals(new java.math.BigDecimal("4.1234567890123456789"), response.path("indicadorBcrp").path("valor").decimalValue());
            assertEquals("NO_VERIFICADA", response.path("consultaSenace").path("lkg").path("respaldo").path("cobertura").asText());
            assertTrue(Files.exists(temporary.resolve("store/senace/cartera-proyectos.json")));
            verify(f.transport, times(1)).consultar(Recurso.CARTERA_PROYECTOS);
        });
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "true,true"})
    void sourcesDegradeIndependentlyAndCanBothUseBackup(boolean senaceFails, boolean bcrpFails) {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
            f.economic(2017, true); f.economic(2017, !bcrpFails);
            f.dashboard(2017).andExpect(status().isOk());
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(senaceFails ? failed(Estado.ERROR_HTTP) : received(PROJECTS));
            f.dashboard(2017).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.fuentes.senace").value(senaceFails ? "RESPALDO_LOCAL" : "API_DIRECTA_SENACE"))
                    .andExpect(jsonPath("$.fuentes.bcrp").value(bcrpFails ? "RESPALDO_LOCAL" : "API_DIRECTA_BCRP"))
                    .andExpect(jsonPath("$.proyectos[0].origen").value(senaceFails ? "RESPALDO_LOCAL" : "API_DIRECTA_SENACE"))
                    .andExpect(jsonPath("$.consultaSenace.lkg.intentoApi.estado").value(senaceFails ? "ERROR_EXTERNO" : "EXITO"))
                    .andExpect(jsonPath("$.indicadorBcrp.lkg.intentoApi.estado").value(bcrpFails ? "ERROR_EXTERNO" : "EXITO"));
        });
    }

    @Test void failingWithoutBackupsDoesNotPublishOfficialZeros() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(failed(Estado.ERROR_HTTP));
            f.economic(2017, false);
            f.dashboard(2017).andExpect(status().isOk()).andExpect(jsonPath("$.total").doesNotExist())
                    .andExpect(jsonPath("$.proyectos").doesNotExist())
                    .andExpect(jsonPath("$.fuentes.senace").value("SIN_RESPALDO"))
                    .andExpect(jsonPath("$.fuentes.bcrp").value("SIN_RESPALDO"))
                    .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotExist())
                    .andExpect(jsonPath("$.indicadorBcrp.codigoSerie").value("PM04973AA"))
                    .andExpect(jsonPath("$.consultaSenace.lkg.lectura").value("MISSING"));
        });
    }

    @Test void anotherYearNeverReusesEconomicBackupButSenaceRetainsFullResource() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
            f.economic(2017, true); f.economic(2018, false);
            f.dashboard(2017).andExpect(status().isOk());
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(failed(Estado.ERROR_HTTP));
            f.dashboard(2018).andExpect(status().isOk())
                    .andExpect(jsonPath("$.fuentes.senace").value("RESPALDO_LOCAL"))
                    .andExpect(jsonPath("$.fuentes.bcrp").value("SIN_RESPALDO"))
                    .andExpect(jsonPath("$.indicadorBcrp.lkg.lectura").value("MISSING"))
                    .andExpect(jsonPath("$.indicadorBcrp.valor").doesNotExist());
        });
    }

    @Test void explicitEconomicAbsenceDoesNotMaskItselfWithHistoricalValue() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
            f.economic(2017, true);
            f.http.server.expect(requestTo(BASE + "/PM04973AA/json/2017/2017/esp"))
                    .andRespond(withSuccess(ECONOMIC.replace("[{\"name\":\"2017\",\"values\":[\"4.1234567890123456789\"]}]", "[]"), MediaType.APPLICATION_JSON));
            f.dashboard(2017).andExpect(status().isOk());
            f.dashboard(2017).andExpect(status().isOk()).andExpect(jsonPath("$.indicadorBcrp.valor").doesNotExist())
                    .andExpect(jsonPath("$.indicadorBcrp.motivo").value("SIN_OBSERVACION"))
                    .andExpect(jsonPath("$.indicadorBcrp.lkg.intentoApi.estado").value("SIN_OBSERVACION"))
                    .andExpect(jsonPath("$.indicadorBcrp.lkg.lectura").doesNotExist());
        });
    }

    @Test void consultantBackupExposesAgeAttemptAndCorsWithoutChangingArrayOrRows() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES))
                    .thenReturn(received("[" + CONSULTANT + "," + CONSULTANT + "]"), failed(Estado.ERROR_HTTP));
            MvcResult first = f.get("/api/consultoras").andExpect(status().isOk())
                    .andExpect(header().string("X-Senace-Snapshot-Escritura", "SAVED")).andReturn();
            String capture = first.getResponse().getHeader("X-Senace-Respaldo-Capturado-En");
            assertNotNull(capture);
            f.clock.advance(Duration.ofDays(2));
            MvcResult second = f.get("/api/consultoras").andExpect(status().isOk())
                    .andExpect(header().string("X-Senace-Origen", "RESPALDO_LOCAL"))
                    .andExpect(header().string("X-Senace-Respaldo-Antiguo", "true"))
                    .andExpect(header().string("X-Senace-Respaldo-Capturado-En", capture))
                    .andExpect(header().string("X-Senace-Intento-Motivo", "ERROR_HTTP"))
                    .andExpect(header().string("X-Senace-Cuerpo-Completo", "true"))
                    .andExpect(header().string("X-Senace-Snapshot-Lectura", "AVAILABLE"))
                    .andExpect(jsonPath("$[0].razonSocial").value("CONSULTORA ARTIFICIAL"))
                    .andExpect(jsonPath("$[0].ruc").doesNotExist()).andReturn();
            assertEquals(2, f.body(second).size());
            assertTrue(second.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)
                    .contains("X-Senace-Respaldo-Capturado-En"));
            assertTrue(Long.parseLong(second.getResponse().getHeader("X-Senace-Respaldo-Edad-Segundos")) >= 172800);
            f.mvc.perform(options("/api/consultoras").header(HttpHeaders.ORIGIN, "https://frontend.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andExpect(status().isOk());
            verify(f.transport, times(2)).consultar(Recurso.CONSULTORAS_AMBIENTALES);
        });
    }

    @Test void validEmptyDoesNotBecomeBackupAndDoesNotReplaceNonemptySnapshot() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES))
                    .thenReturn(received("[" + CONSULTANT + "]"), received("[]"), failed(Estado.ERROR_HTTP));
            f.get("/api/consultoras").andExpect(status().isOk());
            f.get("/api/consultoras").andExpect(status().isOk()).andExpect(content().json("[]"))
                    .andExpect(header().string("X-Senace-Origen", "API_DIRECTA_SENACE"))
                    .andExpect(header().string("X-Senace-Estado", "VACIA"))
                    .andExpect(header().string("X-Senace-Snapshot-Escritura", "EMPTY_REPLACEMENT_REJECTED"))
                    .andExpect(header().doesNotExist("X-Senace-Respaldo-Capturado-En"));
            f.get("/api/consultoras").andExpect(status().isOk())
                    .andExpect(header().string("X-Senace-Origen", "RESPALDO_LOCAL"))
                    .andExpect(jsonPath("$[0].razonSocial").value("CONSULTORA ARTIFICIAL"));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "corrupt"})
    void unusableSnapshotsAreNotServedEvenWithProviderFormatError(String kind) {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES))
                    .thenReturn(received("[" + CONSULTANT + "]"), received("[{\"RAZON_SOCIAL\":123}]"));
            f.get("/api/consultoras").andExpect(status().isOk());
            if (kind.equals("expired")) f.clock.advance(Duration.ofDays(91));
            else Files.writeString(temporary.resolve("store/senace/consultoras-ambientales.json"), "{corrupto");
            f.get("/api/consultoras").andExpect(status().isBadGateway()).andExpect(content().json("[]"))
                    .andExpect(header().string("X-Senace-Origen", "SIN_RESPALDO"))
                    .andExpect(header().string("X-Senace-Snapshot-Lectura", kind.equals("expired") ? "EXPIRED" : "INVALID"))
                    .andExpect(header().doesNotExist("X-Senace-Respaldo-Capturado-En"));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "format", "configuration"})
    void consultantErrorsWithoutBackupKeepArrayRootAndSafeStatus(String kind) {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(kind.equals("format")
                    ? received("{\"error\":true}") : failed(kind.equals("configuration") ? Estado.CONFIGURACION_AUSENTE : Estado.ERROR_HTTP));
            f.get("/api/consultoras").andExpect(status().is(kind.equals("format") ? 502 : 503))
                    .andExpect(content().json("[]")).andExpect(header().string("X-Senace-Origen", "SIN_RESPALDO"))
                    .andExpect(header().string("X-Senace-Snapshot-Lectura", "MISSING"));
        });
    }

    @Test void circuitOpenUsesBackupAndMonitorDoesNotCallTransport() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(received("[" + CONSULTANT + "]"));
            f.get("/api/consultoras").andExpect(status().isOk());
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(failed(Estado.ERROR_HTTP));
            CircuitBreaker breaker = f.registry.circuitBreaker("senace");
            int threshold = breaker.getCircuitBreakerConfig().getSlidingWindowSize();
            int attempts = 0;
            while (breaker.getState() == CircuitBreaker.State.CLOSED && attempts < threshold) {
                f.get("/api/consultoras").andExpect(status().isOk()); attempts++;
            }
            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
            f.get("/api/consultoras").andExpect(status().isOk())
                    .andExpect(header().string("X-Senace-Origen", "RESPALDO_LOCAL"))
                    .andExpect(header().string("X-Senace-Intento-Motivo", "CIRCUITO_ABIERTO"));
            f.get("/api/estado-servicios").andExpect(status().isOk())
                    .andExpect(jsonPath("$.senace.estado").value("OPEN"))
                    .andExpect(jsonPath("$.senace.respaldoLocal").value("OPERATIVO"))
                    .andExpect(jsonPath("$.senace.disponibilidadExterna").value("NO_VERIFICADA"));
            verify(f.transport, times(attempts + 1)).consultar(Recurso.CONSULTORAS_AMBIENTALES);
        });
    }

    @Test void genericRemainsDirectAndDoesNotCaptureEvenWhenLkgIsEnabled() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
            f.get("/api/senace/datastream?nombre=CarteraProyectos").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.consulta.lkg").doesNotExist());
            assertFalse(Files.exists(temporary.resolve("store")));
        });
    }

    @Test void repositoryReconstructionPreservesDataButMonitorStartsUnverified() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(received("[" + CONSULTANT + "]"));
            f.get("/api/consultoras").andExpect(status().isOk());
        });
        scenario(true, f -> {
            f.clock.advance(Duration.ofDays(2));
            f.get("/api/estado-servicios").andExpect(status().isOk())
                    .andExpect(jsonPath("$.lkg.estado").value("HABILITADO_SIN_VERIFICACION"))
                    .andExpect(jsonPath("$.lkg.observaciones").isEmpty());
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(failed(Estado.ERROR_HTTP));
            f.get("/api/consultoras").andExpect(status().isOk())
                    .andExpect(header().string("X-Senace-Origen", "RESPALDO_LOCAL"))
                    .andExpect(header().string("X-Senace-Respaldo-Antiguo", "true"));
        });
    }

    @Test void boundedDiskWriteFailureDoesNotLoseDirectResponseAndIsObservable() {
        context(true, temporary.resolve("small")).withPropertyValues("ecoinvest.snapshots.max-file-bytes=1024").run(ctx -> {
            assertNull(ctx.getStartupFailure());
            SenaceTransport transport = ctx.getBean(SenaceTransport.class);
            when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(received(
                    "[{\"RAZON_SOCIAL\":\"" + "ARTIFICIAL".repeat(300) + "\"}]"));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx.getSourceApplicationContext()).build();
            mvc.perform(get("/api/consultoras")).andExpect(status().isOk())
                    .andExpect(header().string("X-Senace-Origen", "API_DIRECTA_SENACE"))
                    .andExpect(header().string("X-Senace-Snapshot-Escritura", "IO_ERROR"))
                    .andExpect(header().doesNotExist("X-Senace-Respaldo-Capturado-En"))
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get("/api/estado-servicios")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.lkg.estado").value("CON_INCIDENCIAS"));
            assertFalse(Files.exists(temporary.resolve("small/senace/consultoras-ambientales.json")));
            verify(transport, times(1)).consultar(Recurso.CONSULTORAS_AMBIENTALES);
            ctx.getBean(HttpFixture.class).server.verify();
        });
    }

    @Test void unexpectedSensitiveExceptionDoesNotLeakThroughMetadataOrBody() {
        scenario(true, f -> {
            when(f.transport.consultar(Recurso.CONSULTORAS_AMBIENTALES))
                    .thenThrow(new IllegalStateException("https://example.invalid/?auth_key=ARTIFICIAL_NO_PUBLICAR"));
            MvcResult response = f.get("/api/consultoras").andExpect(status().isServiceUnavailable()).andReturn();
            assertFalse(response.getResponse().getContentAsString().contains("ARTIFICIAL_NO_PUBLICAR"));
            assertFalse(response.getResponse().getHeaderNames().stream()
                    .anyMatch(name -> response.getResponse().getHeaders(name).toString().contains("ARTIFICIAL_NO_PUBLICAR")));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean SenaceTransport transport() { return mock(SenaceTransport.class); }
        @Bean SenaceClient senace(SenaceTransport transport, ObjectMapper json) { return new SenaceClient(transport, json); }
        @Bean HttpFixture httpFixture() { return new HttpFixture(); }
        @Bean BcrpClient bcrp(HttpFixture fixture, ObjectMapper json) { return new BcrpClient(fixture.rest, json); }
        @Bean JsonNullableModule nullableModule() { return new AppConfig().jsonNullableModule(); }
        @Bean("snapshotClock") TestClock clock() { return new TestClock(); }
        @Bean EcoInvestController controller(SenaceClient senace, BcrpClient bcrp, CircuitBreakerRegistry registry) {
            return new EcoInvestController(senace, bcrp, registry);
        }
    }
    static class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException(); return this; }
        @Override public Instant instant() { return now.get(); }
    }
    static class HttpFixture {
        final RestTemplate rest = new RestTemplate();
        final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    }
    private record Fixture(MockMvc mvc, SenaceTransport transport, HttpFixture http,
            ObjectMapper json, TestClock clock, CircuitBreakerRegistry registry) {
        ResultActions get(String path) {
            clock.advance(Duration.ofSeconds(1));
            try { return mvc.perform(LkgPublicIntegrationTest.request(path)); }
            catch (Exception failure) { throw new IllegalStateException("FALLO_MVC_ARTIFICIAL", failure); }
        }
        ResultActions dashboard(int year) { return get("/api/dashboard?sector=MINERIA&anio=" + year); }
        JsonNode body(MvcResult response) {
            try { return json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }
        void economic(int year, boolean success) {
            var expectation = http.server.expect(requestTo(BASE + "/PM04973AA/json/" + year + "/" + year + "/esp"))
                    .andExpect(method(HttpMethod.GET));
            if (success) expectation.andRespond(withSuccess(ECONOMIC, MediaType.APPLICATION_JSON));
            else expectation.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path) {
        return get(path).header(HttpHeaders.ORIGIN, "https://frontend.example");
    }
    private static RespuestaTransporte received(String body) {
        int size = body.getBytes(StandardCharsets.UTF_8).length;
        return new RespuestaTransporte(Estado.RECIBIDO, 200, TipoContenido.JSON, "application/json", size, (long) size, true, body);
    }
    private static RespuestaTransporte failed(Estado state) {
        return new RespuestaTransporte(state, state == Estado.CONFIGURACION_AUSENTE ? null : 503,
                TipoContenido.DESCONOCIDO, null, 0, null, false, null);
    }
}
