package com.ecoinvest.soa.integration;

import com.ecoinvest.soa.client.*;
import com.ecoinvest.soa.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static com.ecoinvest.soa.snapshot.ResultadoLkg.Origen.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** AOP real con politica/perfil reales, sin Boot completo, persistencia JPA ni sockets. */
class LkgCoordinationIntegrationTest {
    @TempDir Path temporary;
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    private static final String URL = "https://bcrp.invalid/api/PM04973AA/json/2017/2017/esp";
    private static final String ECONOMIC = """
            {"config":{"series":[{"name":"Producto bruto interno por sectores productivos (variación porcentual interanual) - Minería e Hidrocarburos - Minería Metálica"}]},
             "periods":[{"name":"2017","values":["4.1234567890123456789"]}]}
            """;

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=integracion-local",
                        "external.apis.bcrp-base-url=https://bcrp.invalid/api",
                        "ecoinvest.snapshots.enabled=false")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class))
                .withUserConfiguration(Beans.class);
    }

    private JsonFileSnapshotRepository repository() {
        return new JsonFileSnapshotRepository(new SnapshotProperties(temporary.resolve("store"), 1024 * 1024,
                Duration.parse("PT24H"), Duration.parse("P90D"), Duration.parse("P7D"), Duration.parse("P365D")),
                new SnapshotValidator(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void realProfileAndProxiesRemainIsolatedAndCoordinationIsNotPubliclyRegistered() {
        context().run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertArrayEquals(new String[] {"integracion-local"}, ctx.getEnvironment().getActiveProfiles());
            String exclusions = ctx.getEnvironment().getProperty("spring.autoconfigure.exclude[0]");
            assertNotNull(exclusions);
            assertTrue(ctx.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(ctx.getBeansOfType(EntityManagerFactory.class).isEmpty());
            assertTrue(ctx.getBeansOfType(EntityManager.class).isEmpty());
            assertTrue(ctx.getBeansOfType(ServletWebServerFactory.class).isEmpty());
            assertTrue(ctx.getBeansOfType(SenaceSnapshotCoordinator.class).isEmpty());
            assertTrue(ctx.getBeansOfType(BcrpSnapshotCoordinator.class).isEmpty());
            assertTrue(AopUtils.isAopProxy(ctx.getBean(SenaceClient.class)));
            assertTrue(AopUtils.isAopProxy(ctx.getBean(BcrpClient.class)));
            verifyNoInteractions(ctx.getBean(SenaceTransport.class));
            ctx.getBean(HttpFixture.class).server.verify();
        });
    }

    @Test void senaceCircuitOpensFromRealFailuresAndLocalRecoveryNeverCallsTransportAgain() {
        context().run(ctx -> {
            assertNull(ctx.getStartupFailure());
            var client = ctx.getBean(SenaceClient.class);
            var transport = ctx.getBean(SenaceTransport.class);
            var breaker = ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("senace");
            when(transport.consultar(SenaceTransport.Recurso.CARTERA_PROYECTOS)).thenReturn(received(
                    "[{\"ID\":1,\"ACTIVIDAD\":\"MINERIA\",\"FECHA_INICIO\":\"01/01/2017\"}]"));
            try (var repository = repository()) {
                var coordinator = new SenaceSnapshotCoordinator(client, repository, ctx.getBean(ObjectMapper.class), Clock.fixed(now, ZoneOffset.UTC));
                assertEquals(API_DIRECTA_SENACE, coordinator.consultarProyectos("MINERIA", 2017).origen());
                when(transport.consultar(SenaceTransport.Recurso.CARTERA_PROYECTOS)).thenReturn(failed());
                int threshold = Math.min(breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                        breaker.getCircuitBreakerConfig().getSlidingWindowSize());
                int attempts = 0;
                while (breaker.getState() == CircuitBreaker.State.CLOSED && attempts < threshold) {
                    assertEquals(RESPALDO_LOCAL, coordinator.consultarProyectos("MINERIA", 2017).origen());
                    attempts++;
                }
                assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
                int failures = breaker.getMetrics().getNumberOfFailedCalls();
                var result = coordinator.consultarProyectos("MINERIA", 2017);
                assertEquals(RESPALDO_LOCAL, result.origen());
                assertEquals("CIRCUITO_ABIERTO", result.intentoApi().motivo());
                assertEquals(failures, breaker.getMetrics().getNumberOfFailedCalls());
                assertEquals(1, breaker.getMetrics().getNumberOfNotPermittedCalls());
                verify(transport, times(attempts + 1)).consultar(SenaceTransport.Recurso.CARTERA_PROYECTOS);
                verify(transport, never()).consultar(SenaceTransport.Recurso.CONSULTORAS_AMBIENTALES);
            }
        });
    }

    @Test void bcrpCircuitOpenRecoversExactObservationWithoutAnotherHttpAttempt() {
        context().run(ctx -> {
            assertNull(ctx.getStartupFailure());
            var http = ctx.getBean(HttpFixture.class);
            var client = ctx.getBean(BcrpClient.class);
            var breaker = ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("bcrp");
            http.server.expect(requestTo(URL)).andRespond(withSuccess(ECONOMIC, MediaType.APPLICATION_JSON));
            try (var repository = repository()) {
                var coordinator = new BcrpSnapshotCoordinator(client, repository, Clock.fixed(now, ZoneOffset.UTC));
                assertEquals(API_DIRECTA_BCRP, coordinator.consultar("MINERIA", 2017).origen());
                http.server.verify(); http.server.reset();
                int threshold = Math.min(breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                        breaker.getCircuitBreakerConfig().getSlidingWindowSize());
                var requests = new java.util.concurrent.atomic.AtomicInteger();
                http.server.expect(ExpectedCount.between(1, threshold), requestTo(URL)).andRespond(request -> {
                    requests.incrementAndGet();
                    return withStatus(HttpStatus.SERVICE_UNAVAILABLE).createResponse(request);
                });
                int attempts = 0;
                while (breaker.getState() == CircuitBreaker.State.CLOSED && attempts < threshold) {
                    assertEquals(RESPALDO_LOCAL, coordinator.consultar("MINERIA", 2017).origen());
                    attempts++;
                }
                assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
                assertEquals(attempts, requests.get());
                assertEquals(RESPALDO_LOCAL, coordinator.consultar("MINERIA", 2017).origen());
                assertEquals(attempts, requests.get());
                assertEquals(1, breaker.getMetrics().getNumberOfNotPermittedCalls());
                assertTrue(breaker.getMetrics().getNumberOfFailedCalls() > 0);
                http.server.verify(); // Una llamada adicional fallaria: no hay mas expectativas.
            }
        });
    }

    @Test void storageExceptionDoesNotBecomeProviderFailureOrPublicFallback() {
        context().run(ctx -> {
            assertNull(ctx.getStartupFailure());
            var transport = ctx.getBean(SenaceTransport.class);
            when(transport.consultar(SenaceTransport.Recurso.CARTERA_PROYECTOS)).thenReturn(received("[{\"ID\":1}]"));
            SnapshotRepository broken = mock(SnapshotRepository.class);
            when(broken.save(any(), any(), anyBoolean(), any(), any())).thenThrow(new IllegalStateException("fallo inyectado"));
            var client = ctx.getBean(SenaceClient.class);
            var result = new SenaceSnapshotCoordinator(client, broken, ctx.getBean(ObjectMapper.class),
                    Clock.fixed(now, ZoneOffset.UTC)).consultarProyectos(null, null);
            assertEquals(API_DIRECTA_SENACE, result.origen());
            assertEquals(SnapshotRepository.WriteStatus.IO_ERROR, result.escritura());
            var breaker = ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("senace");
            assertEquals(1, breaker.getMetrics().getNumberOfSuccessfulCalls());
            assertEquals(0, breaker.getMetrics().getNumberOfFailedCalls());
            when(transport.consultar(SenaceTransport.Recurso.CARTERA_PROYECTOS)).thenReturn(failed());
            assertEquals(ResultadoConsultaSenace.Origen.NO_DISPONIBLE, client.consultarProyectos(null, null).origen());
        });
    }

    @Test void bcrpLogicalFormatFailurePreservesExistingBreakerCountingWithoutFalseDirectOrigin() {
        context().run(ctx -> {
            assertNull(ctx.getStartupFailure());
            var http = ctx.getBean(HttpFixture.class);
            http.server.expect(requestTo(URL)).andRespond(withSuccess("no-json", MediaType.APPLICATION_JSON));
            try (var repository = repository()) {
                var result = new BcrpSnapshotCoordinator(ctx.getBean(BcrpClient.class), repository,
                        Clock.fixed(now, ZoneOffset.UTC)).consultar("MINERIA", 2017);
                assertEquals(SIN_RESPALDO, result.origen());
                assertEquals("FORMATO_INVALIDO", result.intentoApi().motivo());
                var metrics = ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("bcrp").getMetrics();
                assertEquals(1, metrics.getNumberOfSuccessfulCalls()); // Retorno normal historico; no acredita API sana.
                assertEquals(0, metrics.getNumberOfFailedCalls());
                http.server.verify();
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean SenaceTransport transport() { return mock(SenaceTransport.class); }
        @Bean SenaceClient senace(SenaceTransport transport, ObjectMapper json) { return new SenaceClient(transport, json); }
        @Bean HttpFixture httpFixture() { return new HttpFixture(); }
        @Bean BcrpClient bcrp(HttpFixture fixture, ObjectMapper json) { return new BcrpClient(fixture.rest, json); }
    }
    static class HttpFixture {
        final RestTemplate rest = new RestTemplate();
        final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    }
    private static SenaceTransport.RespuestaTransporte received(String body) {
        int size = body.getBytes(StandardCharsets.UTF_8).length;
        return new SenaceTransport.RespuestaTransporte(SenaceTransport.Estado.RECIBIDO, 200,
                SenaceTransport.TipoContenido.JSON, "application/json", size, (long) size, true, body);
    }
    private static SenaceTransport.RespuestaTransporte failed() {
        return new SenaceTransport.RespuestaTransporte(SenaceTransport.Estado.ERROR_HTTP, 503,
                SenaceTransport.TipoContenido.DESCONOCIDO, null, 0, null, false, null);
    }
}
