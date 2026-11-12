package com.ecoinvest.soa.integration.lkg;

import com.ecoinvest.soa.client.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** No server in the default suite. Real loopback HTTP requires a SECOND explicit opt-in.
 * Never starts the production application or calls official providers.
 */
class LkgLocalHarnessTest {
    @TempDir Path temporary;
    final ObjectMapper json = new ObjectMapper();

    @Test void fixturesHaveOfficialShapesAndAreExplicitlyArtificial() throws Exception {
        JsonNode projects = json.readTree(LkgLocalHarness.projects());
        assertEquals(45, projects.size());
        assertEquals(13, projects.get(0).size());
        assertEquals("00000000001", projects.get(0).path("RUC").textValue());
        assertEquals("Hidrocarburos", projects.get(0).path("ACTIVIDAD").textValue());
        assertTrue(projects.get(0).path("TITULAR").textValue().contains("ARTIFICIAL"));
        assertTrue(projects.get(4).path("LONGITUD").isNull());
        JsonNode consultants = json.readTree(LkgLocalHarness.consultants());
        assertEquals(24, consultants.size());
        assertEquals(8, consultants.get(0).size());
        assertEquals(consultants.get(0), consultants.get(1));
        assertFalse(consultants.get(0).has("RUC"));
    }

    @Test void configurationHasExplicitPoliciesAndNoCredential() {
        var properties = LkgLocalHarness.properties(temporary);
        assertEquals("true", properties.get("ecoinvest.snapshots.enabled"));
        assertEquals("PT24H", properties.get("ecoinvest.snapshots.senace-stale-after"));
        assertEquals("P90D", properties.get("ecoinvest.snapshots.senace-max-age"));
        assertEquals("P7D", properties.get("ecoinvest.snapshots.bcrp-stale-after"));
        assertEquals("P365D", properties.get("ecoinvest.snapshots.bcrp-max-age"));
        assertEquals("", properties.get("external.apis.senace-auth-key"));
        assertEquals("integracion-local", properties.get("spring.profiles.active"));
    }

    @Test void rejectsUnapprovedRootAndMissingOptInWithoutOpeningServer() {
        assertThrows(IllegalArgumentException.class, () -> LkgLocalHarness.validateRoot(temporary));
        assertThrows(IllegalArgumentException.class, () -> LkgLocalHarness.main(new String[0]));
    }

    @Test void clockAdvancesOnlyExplicitlyInUtc() {
        var clock = new LkgLocalHarness.LabClock();
        var before = clock.instant();
        clock.advance(Duration.ofDays(91));
        assertTrue(Duration.between(before, clock.instant()).compareTo(Duration.ofDays(91)) >= 0);
        assertThrows(IllegalArgumentException.class, () -> clock.withZone(java.time.ZoneId.of("America/Lima")));
    }

    @Test void optionallyExportsOnlyTestClasspathToAuthorizedTemporaryRoot() throws Exception {
        String destination = System.getProperty("ecoinvest.lkg.classpath-output");
        if (destination == null) return;
        Path output = Path.of(destination).toAbsolutePath().normalize();
        LkgLocalHarness.validateRoot(output.getParent());
        if (!output.getFileName().toString().equals("test-classpath.txt"))
            throw new IllegalArgumentException("DESTINO_CLASSPATH_NO_PERMITIDO");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(output, classpath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    @Test
    @EnabledIfSystemProperty(named = "ecoinvest.lkg.local-http-test", matches = "true")
    void actualLocalHttpPersistsAcrossContextsWithoutExternalTransports() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("ecoinvest-lkg-b4-http"));
        Map<Path, byte[]> saved;
        try (var ctx = LkgLocalHarness.start(root, LkgLocalHarness.Scenario.DIRECT)) {
            assertTrue(ctx.getBeansOfType(javax.sql.DataSource.class).isEmpty());
            assertTrue(ctx.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
            assertTrue(ctx.getBeansOfType(jakarta.persistence.EntityManager.class).isEmpty());
            var control = ctx.getBean(LkgLocalHarness.Control.class);
            assertEquals(0, control.senaceCalls.get());
            assertEquals(0, control.bcrpCalls.get());
            JsonNode initialMonitor = body(get("/api/estado-servicios"));
            assertEquals("HABILITADO_SIN_VERIFICACION", initialMonitor.path("lkg").path("estado").textValue());
            assertEquals(0, control.senaceCalls.get());
            assertEquals(0, control.bcrpCalls.get());
            dashboard("API_DIRECTA_SENACE", "API_DIRECTA_BCRP");
            HttpResponse<String> consultants = get("/api/consultoras");
            assertEquals(200, consultants.statusCode());
            assertEquals(24, body(consultants).size());
            assertEquals("API_DIRECTA_SENACE", header(consultants, "X-Senace-Origen"));
            assertTrue(header(consultants, "Access-Control-Expose-Headers").toLowerCase(Locale.ROOT)
                    .contains("x-senace-respaldo-capturado-en"));
            control.scenario.set(LkgLocalHarness.Scenario.FAIL_SENACE);
            dashboard("RESPALDO_LOCAL", "API_DIRECTA_BCRP");
            control.scenario.set(LkgLocalHarness.Scenario.FAIL_BCRP);
            dashboard("API_DIRECTA_SENACE", "RESPALDO_LOCAL");
            control.scenario.set(LkgLocalHarness.Scenario.FAIL_BOTH);
            dashboard("RESPALDO_LOCAL", "RESPALDO_LOCAL");
            saved = snapshotBytes(root);
            assertEquals(3, saved.size());
        }
        // Same directory, genuinely reconstructed context; separate-process restart is browser-session evidence.
        try (var ctx = LkgLocalHarness.start(root, LkgLocalHarness.Scenario.FAIL_BOTH)) {
            assertEquals("HABILITADO_SIN_VERIFICACION", body(get("/api/estado-servicios"))
                    .path("lkg").path("estado").textValue());
            dashboard("RESPALDO_LOCAL", "RESPALDO_LOCAL");
            HttpResponse<String> consultants = get("/api/consultoras");
            assertEquals(200, consultants.statusCode());
            assertEquals("RESPALDO_LOCAL", header(consultants, "X-Senace-Origen"));
            assertFalse(header(consultants, "X-Senace-Respaldo-Capturado-En").isBlank());
            Map<Path, byte[]> after = snapshotBytes(root);
            assertEquals(saved.keySet(), after.keySet());
            saved.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path)));
            JsonNode differentYear = body(get("/api/dashboard?sector=Hidrocarburos&anio=2018"));
            assertEquals("SIN_RESPALDO", differentYear.path("indicadorBcrp").path("origen").textValue());
            var control = ctx.getBean(LkgLocalHarness.Control.class);
            control.clock.advance(Duration.ofDays(2));
            assertEquals("true", header(get("/api/consultoras"), "X-Senace-Respaldo-Antiguo"));
            control.clock.advance(Duration.ofDays(91));
            assertEquals(503, get("/api/consultoras").statusCode());
        }
        Path emptyRoot = Files.createDirectory(temporary.resolve("ecoinvest-lkg-b4-empty"));
        try (var ctx = LkgLocalHarness.start(emptyRoot, LkgLocalHarness.Scenario.INVALID)) {
            HttpResponse<String> response = get("/api/consultoras");
            assertEquals(502, response.statusCode());
            assertEquals("SIN_RESPALDO", header(response, "X-Senace-Origen"));
            assertEquals(0, body(response).size());
        }
    }

    private void dashboard(String senace, String bcrp) throws Exception {
        HttpResponse<String> response = get("/api/dashboard?sector=Hidrocarburos&anio=2017");
        assertEquals(200, response.statusCode());
        JsonNode body = body(response);
        assertEquals(senace, body.path("consultaSenace").path("origen").textValue());
        assertEquals(bcrp, body.path("indicadorBcrp").path("origen").textValue());
        assertEquals(45, body.path("total").intValue());
        assertEquals(22, body.path("aprobados").intValue());
        assertFalse(response.body().contains("auth_key"));
    }
    private static HttpResponse<String> get(String path) throws Exception {
        if (!path.startsWith("/api/") || path.startsWith("//")) throw new IllegalArgumentException();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:8081" + path)).timeout(Duration.ofSeconds(10))
                        .header("Origin", "http://127.0.0.1:5173").GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }
    private static Map<Path, byte[]> snapshotBytes(Path root) throws Exception {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root.resolve("snapshots"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList())
                result.put(root.relativize(path), Files.readAllBytes(path));
        }
        return result;
    }
}
