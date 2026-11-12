package com.ecoinvest.soa.integration.lkg;

import com.ecoinvest.soa.client.*;
import com.ecoinvest.soa.config.AppConfig;
import com.ecoinvest.soa.controller.EcoInvestController;
import com.ecoinvest.soa.snapshot.SnapshotConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.http.*;
import org.springframework.test.web.client.*;
import org.springframework.web.client.RestTemplate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.ecoinvest.soa.client.SenaceTransport.*;

/** TEST CLASSPATH ONLY. Real local HTTP, exclusively ARTIFICIAL provider data.
 * No component scan, provider sockets, credential imports or persistence auto-configuration.
 * stdin: DIRECT / FAIL_SENACE / FAIL_BCRP / FAIL_BOTH / INVALID / PARTIAL,
 * AGE_2_DAYS / AGE_91_DAYS / AGE_366_DAYS / CORRUPT_CONSULTORAS / STATUS / STOP.
 * Start only after explicit server/directory authorization. EOF closes the context.
 */
public final class LkgLocalHarness {
    static final String BASE = "https://bcrp.invalid/api";
    enum Scenario { DIRECT, FAIL_SENACE, FAIL_BCRP, FAIL_BOTH, INVALID, PARTIAL }
    private LkgLocalHarness() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !"--run-lkg-lab".equals(args[0]))
            throw new IllegalArgumentException("OPT_IN_REQUERIDO");
        Path root = validateRoot(Path.of(args[1]));
        Scenario scenario = Scenario.valueOf(args[2]);
        try (var context = start(root, scenario);
             var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("LAB_READY ARTIFICIAL_DATA 127.0.0.1:8081 PID=" + ProcessHandle.current().pid());
            for (String line; (line = input.readLine()) != null;) {
                String command = line.trim();
                if (command.equals("STOP")) break;
                try {
                    var control = context.getBean(Control.class);
                    switch (command) {
                        case "AGE_2_DAYS" -> control.clock.advance(Duration.ofDays(2));
                        case "AGE_91_DAYS" -> control.clock.advance(Duration.ofDays(91));
                        case "AGE_366_DAYS" -> control.clock.advance(Duration.ofDays(366));
                        case "CORRUPT_CONSULTORAS" -> {
                            Path file = root.resolve("snapshots/senace/consultoras-ambientales.json");
                            rejectLinks(file);
                            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                                throw new IllegalStateException("SNAPSHOT_PREVIO_REQUERIDO");
                            Files.writeString(file, "{ARTIFICIAL_CORRUPTION", StandardCharsets.UTF_8,
                                    StandardOpenOption.TRUNCATE_EXISTING);
                        }
                        case "STATUS" -> System.out.println("LAB_COUNTS senace=" + control.senaceCalls.get()
                                + " bcrp=" + control.bcrpCalls.get() + " scenario=" + control.scenario.get());
                        default -> control.scenario.set(Scenario.valueOf(command));
                    }
                    System.out.println("LAB_COMMAND_OK " + command);
                } catch (RuntimeException failure) {
                    // Never print request URIs, external bodies or exception causes.
                    System.out.println("LAB_COMMAND_REJECTED");
                }
            }
        }
        System.out.println("LAB_STOPPED");
    }

    static Path validateRoot(Path value) throws Exception {
        Path root = value.toAbsolutePath().normalize();
        Path temporary = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize().toRealPath();
        if (!root.getFileName().toString().startsWith("ecoinvest-lkg-b4-")
                || !root.startsWith(temporary) || root.equals(temporary))
            throw new IllegalArgumentException("DIRECTORIO_TEMPORAL_DEDICADO_REQUERIDO");
        rejectLinks(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().startsWith(temporary))
            throw new IllegalArgumentException("DIRECTORIO_PREAUTORIZADO_REQUERIDO");
        return root;
    }

    static void rejectLinks(Path path) {
        for (Path p = path; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("ENLACE_NO_ADMITIDO");
    }

    static Map<String, Object> properties(Path root) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.config.location", "classpath:/application.yml");
        properties.put("spring.config.additional-location", "");
        properties.put("spring.config.import", "");
        properties.put("spring.profiles.include", "");
        properties.put("spring.profiles.active", "integracion-local");
        properties.put("external.apis.bcrp-base-url", BASE);
        properties.put("external.apis.senace-base-url", "");
        properties.put("external.apis.senace-auth-key", "");
        properties.put("ecoinvest.snapshots.enabled", "true");
        properties.put("ECOINVEST_SNAPSHOT_DIR", root.resolve("snapshots").toString());
        properties.put("ecoinvest.snapshots.senace-stale-after", "PT24H");
        properties.put("ecoinvest.snapshots.senace-max-age", "P90D");
        properties.put("ecoinvest.snapshots.bcrp-stale-after", "P7D");
        properties.put("ecoinvest.snapshots.bcrp-max-age", "P365D");
        properties.put("spring.main.banner-mode", "off");
        return properties;
    }

    static ServletWebServerApplicationContext start(Path directory, Scenario scenario) throws Exception {
        Path root = validateRoot(directory);
        var environment = new StandardServletEnvironment();
        // Do not resolve system/environment secrets or application overrides into this laboratory.
        environment.getPropertySources().remove("systemEnvironment");
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().addFirst(new MapPropertySource("lkgLab", properties(root)));
        var application = new SpringApplication(Beans.class, SnapshotConfiguration.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setAddCommandLineProperties(false);
        application.setRegisterShutdownHook(true);
        application.addInitializers(ctx -> {
            var env = ctx.getEnvironment();
            if (!Arrays.equals(env.getActiveProfiles(), new String[]{"integracion-local"})
                    || !"127.0.0.1".equals(env.getProperty("server.address"))
                    || !"8081".equals(env.getProperty("server.port")))
                throw new IllegalStateException("PERFIL_O_BIND_INSEGURO");
            Set<String> exclusions = new HashSet<>();
            for (int i = 0; i < 3; i++) exclusions.add(env.getProperty("spring.autoconfigure.exclude[" + i + "]"));
            if (!exclusions.equals(Set.of("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                    "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration")))
                throw new IllegalStateException("EXCLUSIONES_INSEGURAS");
            ctx.getBeanFactory().registerSingleton("labControl", new Control(scenario));
            ctx.addBeanFactoryPostProcessor(factory -> {
                for (Class<?> type : List.of(DataSource.class, EntityManagerFactory.class, EntityManager.class))
                    if (factory.getBeanNamesForType(type, true, false).length != 0)
                        throw new IllegalStateException("PERSISTENCIA_NO_PERMITIDA");
            });
        });
        var context = (ServletWebServerApplicationContext) application.run();
        if (!AopUtils.isAopProxy(context.getBean(SenaceClient.class))
                || !AopUtils.isAopProxy(context.getBean(BcrpClient.class))) {
            context.close();
            throw new IllegalStateException("PROXY_AOP_REQUERIDO");
        }
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({AopAutoConfiguration.class, JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, ServletWebServerFactoryAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class})
    static class Beans {
        @Bean FilterRegistrationBean<jakarta.servlet.Filter> labIdentity() {
            var registration = new FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setFilter((request, response, chain) -> {
                ((jakarta.servlet.http.HttpServletResponse) response).setHeader("X-EcoInvest-Lab", "ARTIFICIAL");
                chain.doFilter(request, response);
            });
            registration.addUrlPatterns("/*");
            return registration;
        }
        @Bean SenaceTransport transport(Control control) {
            var transport = mock(SenaceTransport.class);
            when(transport.consultar(any(Recurso.class))).thenAnswer(call -> {
                control.senaceCalls.incrementAndGet();
                Scenario scenario = control.scenario.get();
                if (scenario == Scenario.FAIL_SENACE || scenario == Scenario.FAIL_BOTH)
                    return failed(Estado.ERROR_HTTP);
                if (scenario == Scenario.PARTIAL) return failed(Estado.RESPUESTA_INCOMPLETA);
                if (scenario == Scenario.INVALID) return received("{\"ARTIFICIAL\":true}");
                Recurso resource = call.getArgument(0);
                return switch (resource) {
                    case CARTERA_PROYECTOS -> received(projects());
                    case CONSULTORAS_AMBIENTALES -> received(consultants());
                    default -> failed(Estado.RECURSO_INVALIDO);
                };
            });
            return transport;
        }
        @Bean SenaceClient senace(SenaceTransport transport, ObjectMapper json) {
            return new SenaceClient(transport, json);
        }
        @Bean BcrpClient bcrp(Control control, ObjectMapper json) {
            RestTemplate rest = new RestTemplate();
            // Binding replaces the request factory BEFORE handing the client to Spring/AOP.
            MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
            server.expect(ExpectedCount.manyTimes(), requestTo(org.hamcrest.Matchers.matchesPattern(
                    "https://bcrp\\.invalid/api/PM04974AA/json/(2017|2018)/\\1/esp")))
                    .andExpect(method(HttpMethod.GET)).andRespond(request -> {
                        control.bcrpCalls.incrementAndGet();
                        Scenario scenario = control.scenario.get();
                        if (scenario == Scenario.FAIL_BCRP || scenario == Scenario.FAIL_BOTH)
                            return withStatus(HttpStatus.SERVICE_UNAVAILABLE).createResponse(request);
                        String year = request.getURI().getPath().contains("/2018/") ? "2018" : "2017";
                        String body = "{\"config\":{\"series\":[{\"name\":\"Producto bruto interno por sectores productivos (variaci\u00f3n porcentual interanual) - Miner\u00eda e Hidrocarburos - Hidrocarburos\"}]},"
                                + "\"periods\":[{\"name\":\"" + year + "\",\"values\":[\"1.25\"]}]}";
                        return withSuccess(body, MediaType.APPLICATION_JSON).createResponse(request);
                    });
            return new BcrpClient(rest, json);
        }
        @Bean JsonNullableModule nullable() { return new AppConfig().jsonNullableModule(); }
        @Bean("snapshotClock") Clock clock(Control control) { return control.clock; }
        @Bean EcoInvestController controller(SenaceClient senace, BcrpClient bcrp, CircuitBreakerRegistry registry) {
            return new EcoInvestController(senace, bcrp, registry);
        }
    }

    static final class Control {
        final AtomicReference<Scenario> scenario;
        final AtomicInteger senaceCalls = new AtomicInteger(), bcrpCalls = new AtomicInteger();
        final LabClock clock = new LabClock();
        Control(Scenario scenario) { this.scenario = new AtomicReference<>(scenario); }
    }
    static final class LabClock extends Clock {
        private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);
        void advance(Duration duration) { offset.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("SOLO_UTC");
            return this;
        }
        @Override public Instant instant() { return Instant.now().plus(offset.get()); }
    }
    static String projects() {
        List<String> rows = new ArrayList<>();
        for (int i = 1; i <= 45; i++) rows.add("{\"ID\":" + i
                + ",\"TITULAR\":\"TITULAR ARTIFICIAL\",\"RUC\":\"00000000001\","
                + "\"TITULO_PROYECTO\":\"PROYECTO ARTIFICIAL " + i + "\",\"UNIDAD_PROYECTO\":\"PRUEBA\","
                + "\"TIPO\":\"ARTIFICIAL\",\"ACTIVIDAD\":\"Hidrocarburos\",\"FECHA_INICIO\":\"01/02/2017\","
                + "\"ESTADO\":\"" + (i % 2 == 0 ? "Aprobado" : "Otros") + "\",\"DESCRIPCION\":\"SOLO LABORATORIO\","
                + "\"LONGITUD\":" + (i % 5 == 0 ? "null" : "-77.1234567890123456789")
                + ",\"LATITUD\":" + (i % 5 == 0 ? "null" : "-12.1") + ",\"RESOLUCION\":\"ARTIFICIAL\"}");
        return "[" + String.join(",", rows) + "]";
    }
    static String consultants() {
        String row = "{\"RAZON_SOCIAL\":\"CONSULTORA ARTIFICIAL\",\"ACTIVIDAD\":\"ARTIFICIAL\","
                + "\"SUBSECTOR\":\"PRUEBA\",\"TIPO_SOLICITUD\":\"ARTIFICIAL\",\"RESOLUCION\":\"PRUEBA\","
                + "\"FECHA_RD\":\"01/02/2017\",\"INICIO_VIGENCIA_RD\":\"\",\"FIN_VIGENCIA_RD\":null}";
        return "[" + String.join(",", Collections.nCopies(24, row)) + "]";
    }
    static RespuestaTransporte received(String body) {
        long bytes = body.getBytes(StandardCharsets.UTF_8).length;
        return new RespuestaTransporte(Estado.RECIBIDO, 200, TipoContenido.JSON, "application/json", bytes, bytes, true, body);
    }
    static RespuestaTransporte failed(Estado state) {
        return new RespuestaTransporte(state, 503, TipoContenido.DESCONOCIDO, null, 0, null, false, null);
    }
}
