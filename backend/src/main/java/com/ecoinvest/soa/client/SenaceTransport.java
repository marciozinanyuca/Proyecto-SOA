package com.ecoinvest.soa.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Transporte interno dedicado a SenaceClient.
 * No guarda cuerpos, credenciales o causas externas en logs/excepciones publicas.
 * El plazo acota la espera HTTP y cancela el intercambio; no es un mecanismo
 * para interrumpir de forma forzosa una llamada DNS nativa del sistema operativo.
 */
@Component
public final class SenaceTransport {
    public static final int MAX_BYTES = 6_291_456;
    public static final Duration CONEXION = Duration.ofSeconds(5);
    public static final Duration INACTIVIDAD = Duration.ofSeconds(10);
    public static final Duration PLAZO_TOTAL = Duration.ofSeconds(25);
    private static final String HOST = "datosabiertos.senace.gob.pe";
    private static final String BASE_PATH = "/Api/datastreams";
    private final String baseUrl;
    private final String authKey;
    private final Politica politica;
    private final SessionFactory sessions;

    public enum Recurso {
        CARTERA_PROYECTOS("CarteraProyectos"), CONSULTORAS_AMBIENTALES("ConsultorasAmbientales"),
        // Compatibilidad heredada: rutas y raiz objeto anteriores, no validadas contra el proveedor.
        GASTO_ESPECIFICA("GastoEspecifica", false), GASTO_FUENTE("GastoFuente", false),
        GASTO_GENERICA("GastoGenerica", false), SOLICITUD_ACCESO("SolicitudAcceso", false),
        VISITAS("Visitas", false), RECLAMOS("Reclamos", false);
        private final String nombre;
        private final boolean verificado;
        Recurso(String nombre) { this(nombre, true); }
        Recurso(String nombre, boolean verificado) { this.nombre = nombre; this.verificado = verificado; }
        public String nombre() { return nombre; }
        public boolean verificado() { return verificado; }
        public String ruta() { return BASE_PATH + "/" + nombre + (verificado ? "" : "/data.json"); }
    }

    public enum Estado {
        RECIBIDO, CONFIGURACION_AUSENTE, CONFIGURACION_INVALIDA, DESTINO_INVALIDO, RECURSO_INVALIDO,
        REDIRECCION_RECHAZADA, ERROR_HTTP, ERROR_TLS, ERROR_TRANSPORTE,
        TIMEOUT_CONEXION, TIMEOUT_INACTIVIDAD, PLAZO_TOTAL_AGOTADO,
        LIMITE_TAMANO, RESPUESTA_INCOMPLETA, HTML_INESPERADO,
        JSON_INVALIDO, ESTRUCTURA_INVALIDA, CODIFICACION_NO_ADMITIDA, INTERRUMPIDO
    }

    public enum TipoContenido { JSON, HTML, OTRO, DESCONOCIDO }

    /** Cuerpo solo tras HTTP 200 y documento JSON completo con raiz esperada para el recurso. */
    public record RespuestaTransporte(
            Estado estado, Integer codigoHttp, TipoContenido tipoContenido,
            String contentType, long bytesLeidos, Long longitudDeclarada,
            boolean cuerpoCompleto, String cuerpo) {
        public RespuestaTransporte {
            if (estado != Estado.RECIBIDO && cuerpo != null) {
                throw new IllegalArgumentException("CUERPO_NO_PUBLICABLE");
            }
        }
        @Override public String toString() {
            return "RespuestaTransporte[estado=" + estado + ", codigoHttp=" + codigoHttp
                    + ", tipoContenido=" + tipoContenido + ", bytesLeidos=" + bytesLeidos
                    + ", cuerpoCompleto=" + cuerpoCompleto + "]";
        }
    }

    @Autowired
    public SenaceTransport(
            @Value("${external.apis.senace-base-url:}") String baseUrl,
            @Value("${external.apis.senace-auth-key:}") String authKey) {
        this(baseUrl, authKey, new Politica(CONEXION, INACTIVIDAD, PLAZO_TOTAL, MAX_BYTES),
                SenaceTransport::nuevaSession);
    }

    // Frontera de pruebas sin red. No relaja la validacion publica de destinos.
    SenaceTransport(String baseUrl, String authKey, Politica politica, SessionFactory sessions) {
        this.baseUrl = baseUrl;
        this.authKey = authKey;
        this.politica = java.util.Objects.requireNonNull(politica);
        this.sessions = java.util.Objects.requireNonNull(sessions);
    }

    public RespuestaTransporte consultar(Recurso recurso) {
        if (recurso == null) return vacia(Estado.RECURSO_INVALIDO);
        if (baseUrl == null || baseUrl.isBlank() || authKey == null || authKey.isBlank()) {
            return vacia(Estado.CONFIGURACION_AUSENTE);
        }
        URI base = validarBase(baseUrl);
        if (base == null) return vacia(Estado.DESTINO_INVALIDO);
        if (authKey.chars().anyMatch(Character::isISOControl)) return vacia(Estado.CONFIGURACION_INVALIDA);
        if (Thread.currentThread().isInterrupted()) return vacia(Estado.INTERRUMPIDO);
        URI uri = URI.create("https://" + HOST + recurso.ruta() + "?auth_key="
                + URLEncoder.encode(authKey, StandardCharsets.UTF_8));
        long inicio = System.nanoTime();
        long limite = inicio + politica.total().toNanos();
        Consumidor consumidor = new Consumidor(politica.maxBytes(), limite,
                recurso.verificado() ? JsonToken.START_ARRAY : JsonToken.START_OBJECT);
        Future<RespuestaTransporte> pendiente = null;
        try (Session session = sessions.abrir(politica)) {
            if (System.nanoTime() >= limite) return consumidor.fallo(Estado.PLAZO_TOTAL_AGOTADO);
            if (System.nanoTime() - inicio >= politica.conexion().toNanos()) {
                return consumidor.fallo(Estado.TIMEOUT_CONEXION);
            }
            pendiente = session.ejecutar(uri, consumidor);
            while (true) {
                long ahora = System.nanoTime();
                long restante = limite - ahora;
                if (restante <= 0) return consumidor.fallo(Estado.PLAZO_TOTAL_AGOTADO);
                long presupuestoFase = consumidor.conectado()
                        ? politica.inactividad().toNanos() - (ahora - consumidor.ultimoAvance())
                        : politica.conexion().toNanos() - (ahora - inicio);
                if (presupuestoFase <= 0) return consumidor.fallo(consumidor.conectado()
                        ? Estado.TIMEOUT_INACTIVIDAD : Estado.TIMEOUT_CONEXION);
                try {
                    RespuestaTransporte resultado = pendiente.get(
                            Math.min(restante, Math.min(presupuestoFase, TimeUnit.MILLISECONDS.toNanos(100))),
                            TimeUnit.NANOSECONDS);
                    return System.nanoTime() >= limite
                            ? consumidor.fallo(Estado.PLAZO_TOTAL_AGOTADO) : resultado;
                } catch (TimeoutException esperaBreve) {
                    // Recalcular ambos presupuestos monotonicamente: no reiniciar el total.
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return consumidor.fallo(Estado.INTERRUMPIDO);
        } catch (ExecutionException exception) {
            return consumidor.fallo(clasificar(exception.getCause()));
        } catch (CancellationException exception) {
            return consumidor.fallo(Estado.INTERRUMPIDO);
        } catch (RuntimeException exception) {
            return consumidor.fallo(Estado.ERROR_TRANSPORTE);
        } finally {
            if (pendiente != null && !pendiente.isDone()) pendiente.cancel(true);
            consumidor.releaseResources();
        }
    }

    private static URI validarBase(String value) {
        try {
            URI uri = URI.create(value);
            String path = uri.getRawPath();
            return "https".equalsIgnoreCase(uri.getScheme()) && HOST.equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && (BASE_PATH.equals(path) || (BASE_PATH + "/").equals(path)) ? uri : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static RespuestaTransporte vacia(Estado estado) {
        return new RespuestaTransporte(estado, null, TipoContenido.DESCONOCIDO, null, 0, null, false, null);
    }

    private static Estado clasificar(Throwable exception) {
        for (int i = 0; exception != null && i < 24; i++, exception = exception.getCause()) {
            if (exception instanceof FalloSeguro seguro) return seguro.estado;
            if (exception instanceof SSLException) return Estado.ERROR_TLS;
            if (exception instanceof org.apache.hc.client5.http.ConnectTimeoutException) return Estado.TIMEOUT_CONEXION;
            if (exception instanceof SocketTimeoutException) return Estado.TIMEOUT_INACTIVIDAD;
            if (exception instanceof org.apache.hc.core5.http.ConnectionClosedException) return Estado.RESPUESTA_INCOMPLETA;
        }
        return Estado.ERROR_TRANSPORTE;
    }

    record Politica(Duration conexion, Duration inactividad, Duration total, int maxBytes) {
        Politica {
            if (conexion == null || inactividad == null || total == null || conexion.isNegative()
                    || conexion.isZero() || inactividad.isNegative() || inactividad.isZero()
                    || total.isNegative() || total.isZero() || maxBytes < 1 || maxBytes > MAX_BYTES) {
                throw new IllegalArgumentException("POLITICA_INVALIDA");
            }
        }
    }

    @FunctionalInterface interface SessionFactory { Session abrir(Politica politica); }
    interface Session extends AutoCloseable {
        Future<RespuestaTransporte> ejecutar(URI uri, Consumidor consumidor);
        @Override void close();
    }

    static Session nuevaSession(Politica politica) { return new ApacheSession(politica); }

    private static final class FalloSeguro extends IOException {
        private final Estado estado;
        FalloSeguro(Estado estado) { super(estado.name()); this.estado = estado; }
    }

    /** Un cliente por intercambio: cierre inmediato tambien en exceso, timeout o interrupcion. */
    private static final class ApacheSession implements Session {
        private final CloseableHttpAsyncClient client;
        private volatile Consumidor activo;
        ApacheSession(Politica politica) {
            var manager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setDnsResolver(new DnsResolver() {
                        @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                            comprobarCancelacionDns();
                            InetAddress[] direcciones = SystemDefaultDnsResolver.INSTANCE.resolve(host);
                            comprobarCancelacionDns();
                            // Sin failover implicito que repita intentos sobre varias direcciones.
                            if (direcciones.length == 0) throw new UnknownHostException("DNS_NO_DISPONIBLE");
                            return new InetAddress[] {direcciones[0]};
                        }
                        @Override public String resolveCanonicalHostname(String host) throws UnknownHostException {
                            comprobarCancelacionDns();
                            return host;
                        }
                        private void comprobarCancelacionDns() throws UnknownHostException {
                            Consumidor actual = activo;
                            if (actual == null || actual.cancelado || System.nanoTime() >= actual.deadline) {
                                throw new UnknownHostException("CONSULTA_CANCELADA");
                            }
                        }
                    })
                    .setMaxConnTotal(1).setMaxConnPerRoute(1)
                    .setTlsStrategy(ClientTlsStrategyBuilder.create()
                            .setSslContext(SSLContexts.createSystemDefault()).build())
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.of(politica.conexion()))
                            .setSocketTimeout(Timeout.of(politica.inactividad())).build())
                    .setDefaultTlsConfig(TlsConfig.custom()
                            .setHandshakeTimeout(Timeout.of(politica.conexion()))
                            .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1).build()).build();
            client = HttpAsyncClients.custom().setConnectionManager(manager)
                    .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                    .setIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1)
                            .setSelectInterval(TimeValue.ofMilliseconds(50))
                            .setSoTimeout(Timeout.of(politica.inactividad())).build())
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(Timeout.of(politica.conexion()))
                            .setResponseTimeout(Timeout.of(politica.inactividad()))
                            .setRedirectsEnabled(false).setAuthenticationEnabled(false)
                            .setContentCompressionEnabled(false).setHardCancellationEnabled(true).build())
                    .disableAutomaticRetries().disableRedirectHandling()
                    .disableCookieManagement().disableAuthCaching()
                    .addRequestInterceptorFirst((request, entity, context) -> {
                        Consumidor actual = activo;
                        if (actual != null) actual.conexionEstablecida();
                    })
                    // Despachar en el ejecutor ADMINISTRADO de Apache evita bloquear al llamador en DNS.
                    .addExecInterceptorFirst("senace-dispatch", (request, entity, scope, chain, callback) -> {
                        String marker = SenaceTransport.class.getName() + ".dispatched";
                        if (!Boolean.TRUE.equals(scope.clientContext.getAttribute(marker))) {
                            scope.clientContext.setAttribute(marker, true);
                            scope.scheduler.scheduleExecution(request, entity, scope, callback, TimeValue.ZERO_MILLISECONDS);
                        } else if (scope.cancellableDependency.isCancelled()) {
                            callback.failed(new FalloSeguro(Estado.INTERRUMPIDO));
                        } else {
                            chain.proceed(request, entity, scope, callback);
                        }
                    }).build();
            try {
                client.start();
            } catch (RuntimeException exception) {
                client.close(CloseMode.IMMEDIATE);
                throw exception;
            }
        }
        @Override public Future<RespuestaTransporte> ejecutar(URI uri, Consumidor consumidor) {
            activo = consumidor;
            BasicHttpRequest request = new BasicHttpRequest("GET", uri);
            request.setHeader("Accept", "application/json");
            request.setHeader("Accept-Encoding", "identity");
            request.setHeader("Connection", "close");
            return client.execute(new BasicRequestProducer(request, null), consumidor,
                    HttpClientContext.create(), null);
        }
        @Override public void close() {
            Consumidor actual = activo;
            if (actual != null) actual.cancelado = true;
            client.close(CloseMode.IMMEDIATE);
        }
    }

    /** Limite de bytes de entidad, no de paquetes TCP/TLS que el SO pueda precargar. */
    static final class Consumidor implements AsyncResponseConsumer<RespuestaTransporte> {
        private final int maxBytes;
        private final long deadline;
        private final JsonToken raizEsperada;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
        private final AtomicBoolean terminado = new AtomicBoolean();
        private volatile long ultimoAvance = System.nanoTime();
        private volatile boolean conectado;
        private volatile boolean cancelado;
        private FutureCallback<RespuestaTransporte> callback;
        private Integer codigo;
        private String contentType;
        private Long longitud;
        private long bytes;
        private boolean completo;
        private boolean charsetValido = true;
        private boolean descartado;
        private TipoContenido tipo = TipoContenido.DESCONOCIDO;

        Consumidor(int maxBytes, long deadline) { this(maxBytes, deadline, JsonToken.START_ARRAY); }
        Consumidor(int maxBytes, long deadline, JsonToken raizEsperada) {
            this.maxBytes = maxBytes;
            this.deadline = deadline;
            this.raizEsperada = raizEsperada;
        }
        long ultimoAvance() { return ultimoAvance; }
        boolean conectado() { return conectado; }
        void conexionEstablecida() { ultimoAvance = System.nanoTime(); conectado = true; }

        @Override public synchronized void consumeResponse(HttpResponse response, EntityDetails entity,
                HttpContext context, FutureCallback<RespuestaTransporte> resultCallback) throws IOException {
            comprobarPlazo();
            callback = resultCallback;
            conectado = true;
            codigo = response.getCode();
            ultimoAvance = System.nanoTime();
            Header ct = response.getFirstHeader("Content-Type");
            if (ct != null) {
                String raw = ct.getValue();
                String media = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                contentType = switch (media) {
                    case "application/json", "text/json", "text/html", "application/xhtml+xml", "text/plain" -> media;
                    default -> "otro";
                };
                // JSON de estos recursos se procesa como UTF-8; no reinterpretar bytes con reemplazos.
                int charset = raw.toLowerCase(Locale.ROOT).indexOf("charset=");
                if (charset >= 0) {
                    String name = raw.substring(charset + 8).split(";", 2)[0].trim().replace("\"", "");
                    charsetValido = "utf-8".equalsIgnoreCase(name) || "utf8".equalsIgnoreCase(name);
                }
            }
            Header encoding = response.getFirstHeader("Content-Encoding");
            if (encoding != null && !"identity".equalsIgnoreCase(encoding.getValue().trim())) {
                throw new FalloSeguro(Estado.CODIFICACION_NO_ADMITIDA);
            }
            if (!charsetValido) throw new FalloSeguro(Estado.CODIFICACION_NO_ADMITIDA);
            if (codigo >= 300 && codigo <= 399) throw new FalloSeguro(Estado.REDIRECCION_RECHAZADA);
            if (entity != null && entity.getContentLength() >= 0) longitud = entity.getContentLength();
            if (longitud != null && longitud > maxBytes) throw new FalloSeguro(Estado.LIMITE_TAMANO);
            if (entity == null) finalizar();
        }

        @Override public void informationResponse(HttpResponse response, HttpContext context) throws IOException {
            // Un flujo de 1xx no reinicia el presupuesto de inactividad ni el total.
            comprobarPlazo();
        }

        @Override public synchronized void updateCapacity(CapacityChannel channel) throws IOException {
            comprobarPlazo();
            channel.update((int) Math.min(8192L, maxBytes - bytes + 1));
        }

        @Override public synchronized void consume(ByteBuffer src) throws IOException {
            comprobarPlazo();
            if (descartado || terminado.get()) throw new FalloSeguro(Estado.INTERRUMPIDO);
            int permitidos = (int) Math.min(src.remaining(), maxBytes - bytes);
            byte[] chunk = new byte[Math.min(8192, permitidos)];
            int restantes = permitidos;
            while (restantes > 0) {
                int n = Math.min(chunk.length, restantes);
                src.get(chunk, 0, n);
                buffer.write(chunk, 0, n);
                bytes += n;
                restantes -= n;
            }
            if (permitidos > 0) ultimoAvance = System.nanoTime();
            if (src.hasRemaining()) {
                src.get(); // Un solo byte centinela autorizado; nunca se almacena como cuerpo.
                bytes++;
                throw new FalloSeguro(Estado.LIMITE_TAMANO);
            }
        }

        @Override public synchronized void streamEnd(List<? extends Header> trailers) throws IOException {
            comprobarPlazo();
            finalizar();
        }

        private void finalizar() throws IOException {
            if (descartado) throw new FalloSeguro(Estado.INTERRUMPIDO);
            if (longitud != null && longitud != bytes) throw new FalloSeguro(Estado.RESPUESTA_INCOMPLETA);
            completo = true;
            String body;
            byte[] rawBody = buffer.toByteArray();
            try {
                body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(rawBody)).toString();
            } catch (CharacterCodingException exception) {
                throw new FalloSeguro(Estado.CODIFICACION_NO_ADMITIDA);
            }
            String inicio = body.stripLeading();
            if (inicio.startsWith("\uFEFF")) inicio = inicio.substring(1).stripLeading();
            tipo = inicio.startsWith("<") ? TipoContenido.HTML : TipoContenido.DESCONOCIDO;
            if (codigo == null || codigo != 200) {
                completar(fallo(Estado.ERROR_HTTP));
                return;
            }
            if (tipo == TipoContenido.HTML) throw new FalloSeguro(Estado.HTML_INESPERADO);
            JsonToken raiz;
            try (JsonParser parser = new JsonFactory().createParser(rawBody)) {
                raiz = parser.nextToken();
                if (raiz == null) throw new FalloSeguro(Estado.JSON_INVALIDO);
                parser.skipChildren();
                if (parser.nextToken() != null) throw new FalloSeguro(Estado.JSON_INVALIDO);
            } catch (FalloSeguro exception) {
                throw exception;
            } catch (IOException exception) {
                throw new FalloSeguro(Estado.JSON_INVALIDO);
            }
            tipo = TipoContenido.JSON;
            if (raiz != raizEsperada) throw new FalloSeguro(Estado.ESTRUCTURA_INVALIDA);
            comprobarPlazo();
            completar(new RespuestaTransporte(Estado.RECIBIDO, codigo, tipo, contentType,
                    bytes, longitud, true, body));
        }

        private void completar(RespuestaTransporte result) {
            if (terminado.compareAndSet(false, true)) callback.completed(result);
        }

        synchronized RespuestaTransporte fallo(Estado estado) {
            return new RespuestaTransporte(estado, codigo, tipo, contentType, bytes, longitud, completo, null);
        }
        private void comprobarPlazo() throws FalloSeguro {
            if (cancelado) throw new FalloSeguro(Estado.INTERRUMPIDO);
            if (System.nanoTime() >= deadline) throw new FalloSeguro(Estado.PLAZO_TOTAL_AGOTADO);
        }
        @Override public synchronized void failed(Exception cause) {
            if (callback != null && terminado.compareAndSet(false, true)) callback.failed(cause);
        }
        @Override public synchronized void releaseResources() {
            descartado = true;
            buffer.reset();
        }
    }
}
