package com.ecoinvest.soa.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecoinvest.soa.client.SenaceTransport.Consumidor;
import com.ecoinvest.soa.client.SenaceTransport.Estado;
import com.ecoinvest.soa.client.SenaceTransport.Politica;
import com.ecoinvest.soa.client.SenaceTransport.Recurso;
import com.ecoinvest.soa.client.SenaceTransport.RespuestaTransporte;
import com.ecoinvest.soa.client.SenaceTransport.Session;
import com.ecoinvest.soa.client.SenaceTransport.TipoContenido;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * Fixtures artificiales. Los casos ordinarios no abren sockets, contexto Spring o SQLite.
 * La clase LocalHttp es opt-in y requiere permiso de servidor local ademas de permiso Maven.
 * Ningun caso consulta SENACE, usa credenciales reales o desactiva TLS.
 */
class SenaceTransportTest {
    private static final String BASE = "https://datosabiertos.senace.gob.pe/Api/datastreams";
    private static final String CLAVE_FICTICIA = "CLAVE_ARTIFICIAL_NO_VALIDA +&/= PRUEBA";
    private static final Politica NORMAL = new Politica(Duration.ofSeconds(5), Duration.ofSeconds(10),
            Duration.ofSeconds(25), SenaceTransport.MAX_BYTES);

    @ParameterizedTest
    @EnumSource(value = Recurso.class, names = {"GASTO_ESPECIFICA", "GASTO_FUENTE", "GASTO_GENERICA", "SOLICITUD_ACCESO", "VISITAS", "RECLAMOS"})
    void legacyResourcesKeepOldObjectRouteWithoutClaimingOfficialValidation(Recurso recurso) {
        FakeSession session = new FakeSession(200, "application/json", "{}", 2L);
        RespuestaTransporte result = transport(session).consultar(recurso);
        assertThat(result.estado()).isEqualTo(Estado.RECIBIDO);
        assertThat(result.cuerpo()).isEqualTo("{}");
        assertThat(recurso.verificado()).isFalse();
        assertThat(session.uri.getPath()).isEqualTo("/Api/datastreams/" + recurso.nombre() + "/data.json");
        assertThat(session.llamadas).hasValue(1);
        assertThat(session.cerrado).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Recurso.class, names = {"GASTO_ESPECIFICA", "GASTO_FUENTE", "GASTO_GENERICA", "SOLICITUD_ACCESO", "VISITAS", "RECLAMOS"})
    void legacyResourceDoesNotAssumeVerifiedArraySchema(Recurso recurso) {
        FakeSession session = new FakeSession(200, "application/json", "[]", 2L);
        assertThat(transport(session).consultar(recurso).estado()).isEqualTo(Estado.ESTRUCTURA_INVALIDA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CARTERA_PROYECTOS", "CONSULTORAS_AMBIENTALES"})
    void onlyApprovedResourceBuildsOneSafeRequest(String name) {
        FakeSession session = new FakeSession(200, "application/json", "[]", 2L);
        RespuestaTransporte result = transport(session).consultar(Recurso.valueOf(name));
        assertThat(result.estado()).isEqualTo(Estado.RECIBIDO);
        assertThat(session.llamadas).hasValue(1);
        assertThat(session.cerrado).isTrue();
        assertThat(session.uri.getScheme()).isEqualTo("https");
        assertThat(session.uri.getHost()).isEqualTo("datosabiertos.senace.gob.pe");
        assertThat(session.uri.getPath()).isEqualTo(Recurso.valueOf(name).ruta()).doesNotContain("data.json");
        assertThat(URLDecoder.decode(session.uri.getRawQuery().substring(9), StandardCharsets.UTF_8))
                .isEqualTo(CLAVE_FICTICIA);
        assertThat(result.toString()).doesNotContain(CLAVE_FICTICIA, "auth_key", "[]");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingCredentialDoesNotCreateHttpClient(String key) {
        AtomicInteger created = new AtomicInteger();
        SenaceTransport transport = new SenaceTransport(BASE, key, NORMAL, p -> {
            created.incrementAndGet();
            throw new AssertionError("NO_DEBE_CREAR_CLIENTE");
        });
        assertThat(transport.consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.CONFIGURACION_AUSENTE);
        assertThat(created).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://datosabiertos.senace.gob.pe/Api/datastreams",
        "https://datosabiertos.senace.gob.pe.ejemplo.invalid/Api/datastreams",
        "https://ejemplo.invalid/Api/datastreams",
        "https://usuario@datosabiertos.senace.gob.pe/Api/datastreams",
        "https://datosabiertos.senace.gob.pe:444/Api/datastreams",
        "https://datosabiertos.senace.gob.pe/Api/datastreams?parametro=1",
        "https://datosabiertos.senace.gob.pe/Api/datastreams#fragmento",
        "https://datosabiertos.senace.gob.pe/Api/datastreams/../Otro",
        "https://datosabiertos.senace.gob.pe/Api/%64atastreams",
        "https://datosabiertos.senace.gob.pe/Api/datastreams/CarteraProyectos",
        "no es una URL"
    })
    void rejectsDestinationsBeforeHttp(String base) {
        FakeSession session = new FakeSession(200, "application/json", "[]", 2L);
        var transport = new SenaceTransport(base, CLAVE_FICTICIA, NORMAL, p -> session);
        assertThat(transport.consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.DESTINO_INVALIDO);
        assertThat(session.llamadas).hasValue(0);
    }

    @Test
    void acceptsOnlyStandardPortAndOptionalFinalSlash() {
        FakeSession session = new FakeSession(200, "application/json", "[]", 2L);
        var transport = new SenaceTransport("https://datosabiertos.senace.gob.pe:443/Api/datastreams/",
                CLAVE_FICTICIA, NORMAL, p -> session);
        assertThat(transport.consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.RECIBIDO);
    }

    @Test
    void absentBaseOrResourceAndControlCharactersNeverInvokeHttp() {
        FakeSession session = new FakeSession(200, "application/json", "[]", 2L);
        assertThat(new SenaceTransport("", CLAVE_FICTICIA, NORMAL, p -> session)
                .consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.CONFIGURACION_AUSENTE);
        assertThat(transport(session).consultar(null).estado()).isEqualTo(Estado.RECURSO_INVALIDO);
        assertThat(new SenaceTransport(BASE, "ficticia\r\n", NORMAL, p -> session)
                .consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.CONFIGURACION_INVALIDA);
        assertThat(session.llamadas).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void rejectsRedirectAndDoesNotFollowLocation(int code) {
        FakeSession session = new FakeSession(code, "text/html", "<html>PRUEBA</html>", null);
        RespuestaTransporte result = transport(session).consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.REDIRECCION_RECHAZADA);
        assertThat(result.codigoHttp()).isEqualTo(code);
        assertThat(result.cuerpo()).isNull();
        assertThat(session.llamadas).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 500, 503})
    void httpErrorsKeepStatusWithoutPublishingBodyOrRetrying(int code) {
        FakeSession session = new FakeSession(code, "application/json", "{\"error\":\"ARTIFICIAL\"}", null);
        RespuestaTransporte result = transport(session).consultar(Recurso.CONSULTORAS_AMBIENTALES);
        assertThat(result.estado()).isEqualTo(Estado.ERROR_HTTP);
        assertThat(result.codigoHttp()).isEqualTo(code);
        assertThat(result.cuerpoCompleto()).isTrue();
        assertThat(result.cuerpo()).isNull();
        assertThat(result.toString()).doesNotContain("ARTIFICIAL");
        assertThat(session.llamadas).hasValue(1);
    }

    @Test
    void validJsonUnderHtmlContentTypeIsNotActualHtml() {
        FakeSession session = new FakeSession(200, "text/html; charset=utf-8", "[{\"ID\":1}]", null);
        RespuestaTransporte result = transport(session).consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.RECIBIDO);
        assertThat(result.tipoContenido()).isEqualTo(TipoContenido.JSON);
        assertThat(result.contentType()).isEqualTo("text/html");
    }

    @Test
    void htmlIsRejectedEvenWhenContentTypeClaimsJson() {
        FakeSession session = new FakeSession(200, "application/json", " <html>PRUEBA</html>", null);
        RespuestaTransporte result = transport(session).consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.HTML_INESPERADO);
        assertThat(result.tipoContenido()).isEqualTo(TipoContenido.HTML);
        assertThat(result.cuerpo()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[{", "no JSON", "[] []", "[1,]"})
    void rejectsInvalidOrMultipleJsonDocuments(String body) {
        assertThat(transport(new FakeSession(200, "application/json", body, null))
                .consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.JSON_INVALIDO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "1", "\"artificial\""})
    void validJsonMustHaveArrayRoot(String body) {
        assertThat(transport(new FakeSession(200, "application/json", body, null))
                .consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.ESTRUCTURA_INVALIDA);
    }

    @Test
    void completeUtf8BodyCanBeMappedUsingUnmodifiedLotOne() throws Exception {
        String json = "[{\"ID\":1,\"ACTIVIDAD\":\"Minería\",\"FECHA_INICIO\":\"01/01/2017\","
                + "\"LONGITUD\":-70.123456789012345678,\"LATITUD\":-12.123456789012345678}]";
        RespuestaTransporte result = transport(new FakeSession(200, "application/json", json, null))
                .consultar(Recurso.CARTERA_PROYECTOS);
        var rows = new ObjectMapper().readValue(result.cuerpo(),
                new com.fasterxml.jackson.core.type.TypeReference<List<com.ecoinvest.soa.client.dto.ProyectoSenaceDto>>() {});
        var mapped = new SenaceMapper().mapearProyectos(rows, "MINERIA", 2017);
        assertThat(mapped.registros()).hasSize(1);
        assertThat(mapped.registros().get(0).longitud()).isEqualByComparingTo("-70.123456789012345678");
        assertThat(result.bytesLeidos()).isEqualTo(json.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void exactProductionLimitIsAcceptedWithoutContentLength() {
        String body = "[" + " ".repeat(SenaceTransport.MAX_BYTES - 2) + "]";
        RespuestaTransporte result = transport(new FakeSession(200, "application/json", body, null))
                .consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.RECIBIDO);
        assertThat(result.bytesLeidos()).isEqualTo(SenaceTransport.MAX_BYTES);
        assertThat(result.longitudDeclarada()).isNull();
        assertThat(result.cuerpoCompleto()).isTrue();
    }

    @Test
    void oneExtraByteRejectsWholeBodyWhilePreservingMaximumBuffer() {
        FakeSession session = new FakeSession(200, "application/json", "[" + " ".repeat(100) + "]", null);
        var transport = new SenaceTransport(BASE, CLAVE_FICTICIA,
                new Politica(NORMAL.conexion(), NORMAL.inactividad(), NORMAL.total(), 32), p -> session);
        RespuestaTransporte result = transport.consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.LIMITE_TAMANO);
        assertThat(result.bytesLeidos()).isEqualTo(33);
        assertThat(result.cuerpo()).isNull();
        assertThat(result.cuerpoCompleto()).isFalse();
        assertThat(session.bytesEntregados.get()).isLessThan(session.body.length);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 500})
    void oversizedDeclaredLengthStopsBeforeBodyEvenForHttpErrors(int status) {
        FakeSession session = new FakeSession(status, "application/json", "[]", (long) SenaceTransport.MAX_BYTES + 1);
        RespuestaTransporte result = transport(session).consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.LIMITE_TAMANO);
        assertThat(result.bytesLeidos()).isZero();
        assertThat(session.bytesEntregados).hasValue(0);
    }

    @Test
    void declaredLengthMismatchIsNeverPublishedAsCompleteJson() {
        RespuestaTransporte result = transport(new FakeSession(200, "application/json", "[]", 10L))
                .consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.RESPUESTA_INCOMPLETA);
        assertThat(result.cuerpoCompleto()).isFalse();
        assertThat(result.cuerpo()).isNull();
    }

    @Test
    void largeErrorBodyWithoutLengthAlsoHasStreamingLimit() {
        FakeSession session = new FakeSession(503, "text/html", "x".repeat(100), null);
        var transport = new SenaceTransport(BASE, CLAVE_FICTICIA,
                new Politica(NORMAL.conexion(), NORMAL.inactividad(), NORMAL.total(), 16), p -> session);
        assertThat(transport.consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.LIMITE_TAMANO);
    }

    @Test
    void capacityNeverRequestsUnlimitedBodyAndAllowsOneProbeByte() throws Exception {
        Consumidor consumer = new Consumidor(2, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
        AtomicInteger capacity = new AtomicInteger();
        consumer.updateCapacity(capacity::set);
        assertThat(capacity).hasValue(3);
        consumer.consume(ByteBuffer.wrap("[]".getBytes(StandardCharsets.UTF_8)));
        consumer.updateCapacity(capacity::set);
        assertThat(capacity).hasValue(1);
        consumer.releaseResources();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "br", "deflate"})
    void unsupportedCompressionRejectedBeforeBuffering(String encoding) {
        FakeSession session = new FakeSession(200, "application/json", "[]", null);
        session.encoding = encoding;
        assertThat(transport(session).consultar(Recurso.CARTERA_PROYECTOS).estado())
                .isEqualTo(Estado.CODIFICACION_NO_ADMITIDA);
        assertThat(session.bytesEntregados).hasValue(0);
    }

    @Test
    void unsupportedCharsetOrInvalidUtf8IsNotSilentlyReplaced() {
        FakeSession charset = new FakeSession(200, "application/json; charset=iso-8859-1", "[]", null);
        assertThat(transport(charset).consultar(Recurso.CARTERA_PROYECTOS).estado())
                .isEqualTo(Estado.CODIFICACION_NO_ADMITIDA);
        FakeSession malformed = new FakeSession(200, "application/json", "[]", null);
        malformed.body = new byte[] {'[', '"', (byte) 0xC3, '"', ']'};
        assertThat(transport(malformed).consultar(Recurso.CARTERA_PROYECTOS).estado())
                .isEqualTo(Estado.CODIFICACION_NO_ADMITIDA);
    }

    @Test
    void totalDeadlineCancelsPendingExchangeAndClosesSessionWithoutThread() {
        CompletableFuture<RespuestaTransporte> pending = new CompletableFuture<>();
        AtomicBoolean closed = new AtomicBoolean();
        Session session = pendingSession(pending, closed);
        Politica shortTotal = new Politica(Duration.ofMillis(10), Duration.ofSeconds(2), Duration.ofMillis(40), 32);
        RespuestaTransporte result = new SenaceTransport(BASE, CLAVE_FICTICIA, shortTotal, p -> session)
                .consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.PLAZO_TOTAL_AGOTADO);
        assertThat(pending.isCancelled()).isTrue();
        assertThat(closed).isTrue();
    }

    @Test
    void inactivityDeadlineIsSeparateFromTotalAndCancelsPendingExchange() {
        CompletableFuture<RespuestaTransporte> pending = new CompletableFuture<>();
        AtomicBoolean closed = new AtomicBoolean();
        Politica shortIdle = new Politica(Duration.ofMillis(10), Duration.ofMillis(30), Duration.ofSeconds(2), 32);
        RespuestaTransporte result = new SenaceTransport(BASE, CLAVE_FICTICIA, shortIdle,
                p -> pendingSession(pending, closed)).consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.TIMEOUT_INACTIVIDAD);
        assertThat(pending.isCancelled()).isTrue();
        assertThat(closed).isTrue();
    }

    @Test
    void connectionDeadlineAlsoCancelsBeforeHttpBecomesReady() {
        CompletableFuture<RespuestaTransporte> pending = new CompletableFuture<>();
        AtomicBoolean closed = new AtomicBoolean();
        Session session = new Session() {
            @Override public Future<RespuestaTransporte> ejecutar(URI uri, Consumidor consumer) { return pending; }
            @Override public void close() { closed.set(true); }
        };
        Politica budget = new Politica(Duration.ofMillis(30), Duration.ofSeconds(2), Duration.ofSeconds(3), 32);
        RespuestaTransporte result = new SenaceTransport(BASE, CLAVE_FICTICIA, budget, p -> session)
                .consultar(Recurso.CARTERA_PROYECTOS);
        assertThat(result.estado()).isEqualTo(Estado.TIMEOUT_CONEXION);
        assertThat(pending.isCancelled()).isTrue();
        assertThat(closed).isTrue();
    }

    @Test
    void callerInterruptionIsPreservedAndPreventsHttp() {
        FakeSession session = new FakeSession(200, "application/json", "[]", null);
        Thread.currentThread().interrupt();
        try {
            assertThat(transport(session).consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.INTERRUMPIDO);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(session.llamadas).hasValue(0);
        } finally { Thread.interrupted(); }
    }

    @Test
    void interruptedWaitCancelsWithoutExposingOriginalException() {
        AtomicBoolean closed = new AtomicBoolean();
        Future<RespuestaTransporte> future = mock(Future.class);
        try {
            when(future.get(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new InterruptedException("MENSAJE_ARTIFICIAL_NO_PUBLICAR"));
            Session session = pendingSession(future, closed);
            RespuestaTransporte result = new SenaceTransport(BASE, CLAVE_FICTICIA, NORMAL, p -> session)
                    .consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(Estado.INTERRUMPIDO);
            assertThat(result.toString()).doesNotContain("MENSAJE_ARTIFICIAL_NO_PUBLICAR");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } catch (Exception exception) { throw new AssertionError(exception); }
        finally { Thread.interrupted(); }
        assertThat(closed).isTrue();
    }

    @Test
    void connectionTlsReadAndTransportFailuresAreSanitizedWithoutRetries() {
        List<Exception> failures = List.of(new SSLHandshakeException("URL_ARTIFICIAL_auth_key=NO_PUBLICAR"),
                new org.apache.hc.client5.http.ConnectTimeoutException("NO_PUBLICAR"),
                new SocketTimeoutException("NO_PUBLICAR"), new IOException("NO_PUBLICAR"));
        List<Estado> states = List.of(Estado.ERROR_TLS, Estado.TIMEOUT_CONEXION,
                Estado.TIMEOUT_INACTIVIDAD, Estado.ERROR_TRANSPORTE);
        for (int i = 0; i < failures.size(); i++) {
            FakeSession session = new FakeSession(200, "application/json", "[]", null);
            session.failure = failures.get(i);
            RespuestaTransporte result = transport(session).consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(states.get(i));
            assertThat(result.toString()).doesNotContain("NO_PUBLICAR", "auth_key");
            assertThat(session.llamadas).hasValue(1);
            assertThat(session.cerrado).isTrue();
        }
    }

    @Test
    void configurationMapsEnvironmentWithoutLoadingLocalCredentialsAndDisablesHttpWireLogging() throws Exception {
        var sources = new YamlPropertySourceLoader().load("test-config", new ClassPathResource("application.yml"));
        var properties = sources.get(0);
        assertThat(properties.getProperty("external.apis.senace-base-url")).isEqualTo("${SENACE_BASE_URL:}");
        assertThat(properties.getProperty("external.apis.senace-auth-key")).isEqualTo("${SENACE_AUTH_KEY:}");
        assertThat(properties.getProperty("spring.config.import")).isNull();
        assertThat(properties.getProperty("logging.level.org.apache.hc.client5.http")).isEqualTo("OFF");
        assertThat(properties.getProperty("logging.level.org.apache.hc.core5.http")).isEqualTo("OFF");
    }

    @Test
    void errorResponseCannotAccidentallyCarryExternalBody() {
        assertThatThrownBy(() -> new RespuestaTransporte(Estado.ERROR_HTTP, 500, TipoContenido.JSON,
                "application/json", 2, 2L, true, "[]")).isInstanceOf(IllegalArgumentException.class);
    }

    private SenaceTransport transport(FakeSession session) {
        return new SenaceTransport(BASE, CLAVE_FICTICIA, NORMAL, p -> session);
    }

    private static Session pendingSession(Future<RespuestaTransporte> future, AtomicBoolean closed) {
        return new Session() {
            @Override public Future<RespuestaTransporte> ejecutar(URI uri, Consumidor consumer) {
                consumer.conexionEstablecida();
                return future;
            }
            @Override public void close() { closed.set(true); }
        };
    }

    /**
     * Integracion HTTP de loopback, nunca proveedor real ni TLS relajado.
     * La envoltura de Session cambia el destino SOLO en esta fixture; la validacion
     * publica sigue recibiendo la URL oficial y no admite localhost en produccion.
     */
    @Nested
    @EnabledIfSystemProperty(named = "ecoinvest.test.local-http", matches = "true")
    class LocalHttp {
        private HttpServer server;
        private ExecutorService workers;
        private URI local;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger redirected = new AtomicInteger();
        private ch.qos.logback.classic.Logger apacheClientLog;
        private ch.qos.logback.classic.Logger apacheCoreLog;
        private ch.qos.logback.classic.Level oldClientLevel;
        private ch.qos.logback.classic.Level oldCoreLevel;

        @BeforeEach void startLoopbackOnly() throws Exception {
            apacheClientLog = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.hc.client5.http");
            apacheCoreLog = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.hc.core5.http");
            oldClientLevel = apacheClientLog.getLevel();
            oldCoreLevel = apacheCoreLog.getLevel();
            apacheClientLog.setLevel(ch.qos.logback.classic.Level.OFF);
            apacheCoreLog.setLevel(ch.qos.logback.classic.Level.OFF);
            workers = Executors.newFixedThreadPool(2);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            local = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/fixture");
            server.createContext("/no-seguir", exchange -> {
                redirected.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
        }

        @AfterEach void stopLoopbackAndWorkers() throws Exception {
            if (server != null) server.stop(0);
            if (workers != null) {
                workers.shutdownNow();
                assertThat(workers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            }
            if (apacheClientLog != null) apacheClientLog.setLevel(oldClientLevel);
            if (apacheCoreLog != null) apacheCoreLog.setLevel(oldCoreLevel);
        }

        @Test void apacheReadsChunkedJsonWithoutContentLength() {
            fixture(exchange -> send(exchange, 200, "application/json", "[]", 0));
            RespuestaTransporte result = localTransport(NORMAL).consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(Estado.RECIBIDO);
            assertThat(result.longitudDeclarada()).isNull();
            assertThat(result.cuerpo()).isEqualTo("[]");
            assertThat(requests).hasValue(1);
        }

        @Test void apacheDoesNotFollowRedirectOrTransmitAnotherRequest() {
            fixture(exchange -> {
                exchange.getResponseHeaders().set("Location", local.resolve("/no-seguir").toString());
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });
            RespuestaTransporte result = localTransport(NORMAL).consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(Estado.REDIRECCION_RECHAZADA);
            assertThat(requests).hasValue(1);
            assertThat(redirected).hasValue(0);
        }

        @Test void apacheDoesNotRetry503EvenWithRetryAfter() {
            fixture(exchange -> {
                exchange.getResponseHeaders().set("Retry-After", "0");
                send(exchange, 503, "application/json", "{}", 2);
            });
            assertThat(localTransport(NORMAL).consultar(Recurso.CARTERA_PROYECTOS).estado()).isEqualTo(Estado.ERROR_HTTP);
            assertThat(requests).hasValue(1);
        }

        @Test void apacheRejectsAuthenticHtml() {
            fixture(exchange -> send(exchange, 200, "text/html", "<html>ARTIFICIAL</html>", 0));
            assertThat(localTransport(NORMAL).consultar(Recurso.CARTERA_PROYECTOS).estado())
                    .isEqualTo(Estado.HTML_INESPERADO);
        }

        @Test void apacheAcceptsJsonMislabeledAsHtml() {
            fixture(exchange -> send(exchange, 200, "text/html; charset=utf-8", "[]", 0));
            RespuestaTransporte result = localTransport(NORMAL).consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(Estado.RECIBIDO);
            assertThat(result.contentType()).isEqualTo("text/html");
        }

        @Test void apacheRejectsEarlyEndOfDeclaredLength() {
            fixture(exchange -> send(exchange, 200, "application/json", "[]", 50));
            RespuestaTransporte result = localTransport(NORMAL).consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(Estado.RESPUESTA_INCOMPLETA);
            assertThat(result.cuerpo()).isNull();
            assertThat(requests).hasValue(1);
        }

        @Test void apacheRejectsStreamingOverrunWithoutPublishingPrefix() {
            fixture(exchange -> send(exchange, 200, "application/json", "[" + " ".repeat(2048) + "]", 0));
            Politica budget = new Politica(CONNECTION, Duration.ofSeconds(2), Duration.ofSeconds(4), 32);
            RespuestaTransporte result = localTransport(budget).consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(Estado.LIMITE_TAMANO);
            assertThat(result.bytesLeidos()).isEqualTo(33);
            assertThat(result.cuerpo()).isNull();
        }

        private static final Duration CONNECTION = Duration.ofSeconds(2);

        @Test void apacheReadInactivityStopsSlowBodyAndDoesNotRetry() {
            fixture(exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                try (var out = exchange.getResponseBody()) {
                    out.write('[');
                    out.flush();
                    try { Thread.sleep(1000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                    out.write(']');
                } finally { exchange.close(); }
            });
            Politica budget = new Politica(CONNECTION, Duration.ofMillis(200), Duration.ofSeconds(4), 4096);
            assertThat(localTransport(budget).consultar(Recurso.CARTERA_PROYECTOS).estado())
                    .isEqualTo(Estado.TIMEOUT_INACTIVIDAD);
            assertThat(requests).hasValue(1);
        }

        @Test void totalDeadlineCancelsSocketEvenWithContinuousProgress() throws Exception {
            CountDownLatch peerObservedClose = new CountDownLatch(1);
            fixture(exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                try (var out = exchange.getResponseBody()) {
                    out.write('[');
                    for (int i = 0; i < 300; i++) {
                        out.write(new byte[1024]);
                        out.flush();
                        try { Thread.sleep(20); }
                        catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
                    }
                } catch (IOException disconnected) { peerObservedClose.countDown(); }
                finally { exchange.close(); }
            });
            Politica budget = new Politica(CONNECTION, Duration.ofSeconds(1), Duration.ofMillis(600), 1024 * 1024);
            long start = System.nanoTime();
            RespuestaTransporte result = localTransport(budget).consultar(Recurso.CARTERA_PROYECTOS);
            assertThat(result.estado()).isEqualTo(Estado.PLAZO_TOTAL_AGOTADO);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
            assertThat(peerObservedClose.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests).hasValue(1);
        }

        private SenaceTransport localTransport(Politica policy) {
            return new SenaceTransport(BASE, CLAVE_FICTICIA, policy, p -> {
                Session real = SenaceTransport.nuevaSession(p);
                return new Session() {
                    @Override public Future<RespuestaTransporte> ejecutar(URI approved, Consumidor consumer) {
                        // No copiar la credencial ni siquiera a este servidor artificial.
                        return real.ejecutar(local, consumer);
                    }
                    @Override public void close() { real.close(); }
                };
            });
        }

        private void fixture(com.sun.net.httpserver.HttpHandler handler) {
            server.createContext("/fixture", exchange -> {
                requests.incrementAndGet();
                handler.handle(exchange);
            });
        }

        private void send(HttpExchange exchange, int status, String contentType, String body, long length)
                throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, length);
            try (var out = exchange.getResponseBody()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            finally { exchange.close(); }
        }
    }

    private static final class FakeSession implements Session {
        private final int code;
        private final String contentType;
        private byte[] body;
        private final Long length;
        private String encoding;
        private Exception failure;
        private URI uri;
        private final AtomicInteger llamadas = new AtomicInteger();
        private final AtomicInteger bytesEntregados = new AtomicInteger();
        private boolean cerrado;
        FakeSession(int code, String contentType, String body, Long length) {
            this.code = code;
            this.contentType = contentType;
            this.body = body.getBytes(StandardCharsets.UTF_8);
            this.length = length;
        }
        @Override public Future<RespuestaTransporte> ejecutar(URI uri, Consumidor consumer) {
            this.uri = uri;
            llamadas.incrementAndGet();
            CompletableFuture<RespuestaTransporte> result = new CompletableFuture<>();
            if (failure != null) { result.completeExceptionally(failure); return result; }
            try {
                BasicHttpResponse response = new BasicHttpResponse(code);
                if (contentType != null) response.setHeader("Content-Type", contentType);
                if (encoding != null) response.setHeader("Content-Encoding", encoding);
                response.setHeader("Location", "https://otro-destino.invalid/NO_SEGUIR");
                EntityDetails details = mock(EntityDetails.class);
                when(details.getContentLength()).thenReturn(length == null ? -1L : length);
                consumer.consumeResponse(response, details, null, new FutureCallback<>() {
                    @Override public void completed(RespuestaTransporte value) { result.complete(value); }
                    @Override public void failed(Exception exception) { result.completeExceptionally(exception); }
                    @Override public void cancelled() { result.cancel(true); }
                });
                for (int offset = 0; offset < body.length; ) {
                    AtomicInteger capacity = new AtomicInteger();
                    consumer.updateCapacity(capacity::set);
                    int count = Math.min(body.length - offset, capacity.get());
                    ByteBuffer chunk = ByteBuffer.wrap(body, offset, count);
                    consumer.consume(chunk);
                    offset += count;
                    bytesEntregados.addAndGet(count);
                }
                consumer.streamEnd(List.of());
            } catch (Exception exception) { result.completeExceptionally(exception); }
            return result;
        }
        @Override public void close() { cerrado = true; }
    }
}
