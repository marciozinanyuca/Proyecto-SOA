package com.ecoinvest.soa.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

// Sin contexto Spring, SQLite o transporte HTTP real. No verifica AOP.
class SenaceClientTest {
    private RestTemplate restTemplate;
    private SenaceClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new SenaceClient(restTemplate);
        ReflectionTestUtils.setField(client, "senaceBaseUrl", "https://senace.invalid/Api/datastreams");
        ReflectionTestUtils.setField(client, "senaceAuthKey", "test-secret-marker");
    }

    @Test
    void fixedRecordsAreSimulatedEvenAfterSuccessfulHttpResponse() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("data", "external"));
        assertThat(client.obtenerProyectos("Hidrocarburos", 2017)).hasSize(2)
                .allSatisfy(p -> assertThat(p).containsEntry("origen", "SIMULADO"));
        assertThat(client.obtenerConsultoras()).hasSize(4)
                .allSatisfy(p -> assertThat(p).containsEntry("origen", "SIMULADO"));
        verify(restTemplate).getForObject(
                "https://senace.invalid/Api/datastreams/CarteraProyectos/data.json?auth_key=test-secret-marker",
                Map.class);
    }

    @Test
    void directFallbacksAreSimulatedOrUnavailableWithoutSqliteClaims() {
        RuntimeException failure = new RuntimeException("auth_key=test-secret-marker");
        assertThat(client.fallbackSenace("Mineria", 2017, failure)).hasSize(2)
                .allSatisfy(p -> assertThat(p).containsEntry("origen", "SIMULADO"));
        assertThat(client.fallbackSenace("Electricidad", 2023, failure)).isEmpty();
        assertThat(client.fallbackConsultoras(failure)).hasSize(4)
                .allSatisfy(p -> assertThat(p).containsEntry("origen", "SIMULADO"));
        Map<String, Object> fallback = client.fallbackDatastreamGenerico("Visitas", failure);
        assertThat(fallback).containsEntry("origen", "NO_DISPONIBLE");
        assertThat(fallback.toString()).doesNotContain("SQLite", "test-secret-marker");
    }

    @Test
    void genericDatastreamPreservesExternalBody() {
        Map<String, Object> body = Map.of("external", "value");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(body);
        assertThat(client.consultarDatastream("Visitas"))
                .containsEntry("data", body).containsEntry("origen", "API_DIRECTA_SENACE");
    }

    @Test
    void authenticatedUrlsAndExternalExceptionMessagesAreNotExposed() {
        Logger logger = (Logger) LoggerFactory.getLogger(SenaceClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("ok", true));
            client.obtenerProyectos("Hidrocarburos", 2017);
            client.obtenerConsultoras();
            client.consultarDatastream("Visitas");
            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                    .thenThrow(new ResourceAccessException("https://senace.invalid?auth_key=test-secret-marker"));
            for (Runnable query : new Runnable[] {
                    () -> client.obtenerProyectos("Hidrocarburos", 2017),
                    () -> client.obtenerConsultoras(), () -> client.consultarDatastream("Visitas")}) {
                Throwable failure = catchThrowable(query::run);
                assertThat(failure).isInstanceOf(RestClientException.class).hasNoCause();
                assertThat(failure.toString()).doesNotContain("auth_key", "test-secret-marker", "https://");
            }
            RuntimeException unsafeFailure = new RuntimeException("auth_key=test-secret-marker");
            client.fallbackSenaceSimple(unsafeFailure);
            client.fallbackConsultoras(unsafeFailure);
            client.fallbackDatastreamGenerico("Visitas", unsafeFailure);
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("auth_key", "test-secret-marker", "https://");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
