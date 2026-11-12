package com.ecoinvest.soa.client;

import com.ecoinvest.soa.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ecoinvest.soa.snapshot.ResultadoLkg.Origen.*;

/** Cliente BCRP y RestTemplate reales con servidor simulado sin sockets. Sin AOP en esta suite. */
class BcrpSnapshotCoordinatorTest {
    @TempDir Path directory;
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate rest = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    private final BcrpClient client = new BcrpClient(rest, json);
    @BeforeEach void configure() { ReflectionTestUtils.setField(client, "bcrpBaseUrl", "https://bcrp.invalid/api"); }
    @AfterEach void verifyRequests() { server.verify(); }
    private JsonFileSnapshotRepository repository(Instant time) {
        return new JsonFileSnapshotRepository(new SnapshotProperties(directory, 1024 * 1024,
                Duration.parse("PT24H"), Duration.parse("P90D"), Duration.parse("P7D"), Duration.parse("P365D")),
                new SnapshotValidator(), Clock.fixed(time, ZoneOffset.UTC));
    }
    private BcrpSnapshotCoordinator coordinator(SnapshotRepository repository, Instant time) {
        return new BcrpSnapshotCoordinator(client, repository, Clock.fixed(time, ZoneOffset.UTC));
    }
    private String body(String year, String value) throws Exception {
        return json.writeValueAsString(Map.of("config", Map.of("series", List.of(Map.of("name",
                "Producto bruto interno por sectores productivos (variación porcentual interanual) - Minería e Hidrocarburos - Minería Metálica"))),
                "periods", List.of(Map.of("name", year, "values", List.of(value)))));
    }
    private void expect(int year, String body) {
        server.expect(requestTo("https://bcrp.invalid/api/PM04973AA/json/" + year + "/" + year + "/esp"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
    private void seed() throws Exception {
        expect(2017, body("2017", "4.1234567890123456789"));
        try (var repository = repository(now)) {
            assertEquals(API_DIRECTA_BCRP, coordinator(repository, now).consultar("MINERIA", 2017).origen());
        }
        server.verify();
        server.reset();
    }

    @Test void directObservationIsSavedAndRecoveredWithExactPrecisionAndCurrentCatalog() throws Exception {
        seed();
        expect(2017, "<html>error artificial</html>");
        try (var repository = repository(now.plusSeconds(1))) {
            var result = coordinator(repository, now.plusSeconds(1)).consultar("MINERIA", 2017);
            assertEquals(RESPALDO_LOCAL, result.origen());
            assertEquals("FORMATO_INVALIDO", result.intentoApi().motivo());
            assertEquals(new BigDecimal("4.1234567890123456789"), result.datos().get("valor"));
            assertEquals("RESPALDO_LOCAL", result.datos().get("origen"));
            assertEquals("ANUAL", result.datos().get("periodicidad"));
            assertEquals("SECTORIAL", result.datos().get("ambito"));
            assertEquals(now, result.respaldo().capturadoEn());
            assertFalse(result.datos().containsKey("urlFuente"));
        }
    }

    @Test void anotherYearCannotUseExistingSnapshot() throws Exception {
        seed(); expect(2018, "no es JSON");
        try (var repository = repository(now)) {
            var result = coordinator(repository, now).consultar("MINERIA", 2018);
            assertEquals(SIN_RESPALDO, result.origen());
            assertNull(result.datos());
            assertEquals(SnapshotRepository.ReadStatus.MISSING, result.lectura());
        }
    }

    @Test void anotherSeriesCannotUseExistingSnapshot() throws Exception {
        seed();
        server.expect(requestTo("https://bcrp.invalid/api/PM04974AA/json/2017/2017/esp"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        try (var repository = repository(now)) {
            var result = coordinator(repository, now).consultar("HIDROCARBUROS", 2017);
            assertEquals(SIN_RESPALDO, result.origen());
        }
    }

    @Test void invalidParametersNeverInvokeTransportOrStorage() {
        var repository = mock(SnapshotRepository.class);
        assertEquals(SIN_RESPALDO, coordinator(repository, now).consultar("NO_CATALOGO", 2017).origen());
        assertEquals(SIN_RESPALDO, coordinator(repository, now).consultar("MINERIA", 0).origen());
        verifyNoInteractions(repository);
    }

    @Test void logicalAbsenceDoesNotOverwriteOrMaskItselfWithOldValue() throws Exception {
        seed();
        expect(2017, "{\"config\":{\"series\":[{\"name\":\"Producto bruto interno por sectores productivos (variación porcentual interanual) - Minería e Hidrocarburos - Minería Metálica\"}]},\"periods\":[]}");
        try (var repository = repository(now.plusSeconds(1))) {
            var result = coordinator(repository, now.plusSeconds(1)).consultar("MINERIA", 2017);
            assertEquals(SIN_RESPALDO, result.origen());
            assertEquals("SIN_OBSERVACION", result.intentoApi().estado());
            assertNull(result.lectura());
            assertEquals(now, repository.read(SnapshotKey.bcrp("PM04973AA", 2017)).snapshot().capturedAt());
        }
    }

    @Test void staleObservationIsUsableButExpiredObservationIsNot() throws Exception {
        seed();
        for (int days : new int[] {8, 365, 366}) {
            expect(2017, "JSON invalido");
            Instant time = now.plus(Duration.ofDays(days));
            try (var repository = repository(time)) {
                var result = coordinator(repository, time).consultar("MINERIA", 2017);
                assertEquals(days > 365 ? SIN_RESPALDO : RESPALDO_LOCAL, result.origen());
                if (days <= 365) assertTrue(result.respaldo().antiguo());
                else assertEquals(SnapshotRepository.ReadStatus.EXPIRED, result.lectura());
            }
            server.verify(); server.reset();
        }
    }

    @Test void failedWriteDoesNotDiscardValidObservation() throws Exception {
        expect(2017, body("2017", "4.25"));
        var repository = mock(SnapshotRepository.class);
        when(repository.save(any(), any(), anyBoolean(), any(), any())).thenThrow(new IllegalStateException("disco artificial"));
        var result = coordinator(repository, now).consultar("MINERIA", 2017);
        assertEquals(API_DIRECTA_BCRP, result.origen());
        assertEquals(new BigDecimal("4.25"), result.datos().get("valor"));
        assertEquals(SnapshotRepository.WriteStatus.IO_ERROR, result.escritura());
        verify(repository, never()).read(any());
    }

    @Test void corruptEconomicSnapshotIsNotServed() throws Exception {
        seed();
        java.nio.file.Files.writeString(directory.resolve("bcrp/PM04973AA/2017.json"), "{corrupto");
        expect(2017, "no-json");
        try (var repository = repository(now)) {
            var result = coordinator(repository, now).consultar("MINERIA", 2017);
            assertEquals(SIN_RESPALDO, result.origen());
            assertEquals(SnapshotRepository.ReadStatus.INVALID, result.lectura());
            assertNull(result.datos());
        }
    }

    @Test void currentPublicFallbackDoesNotExposeInternalBackup() throws Exception {
        seed();
        var result = client.fallbackBcrpPorSector("MINERIA", 2017, new RuntimeException());
        assertEquals("NO_DISPONIBLE", result.get("origen"));
        assertNull(result.get("valor"));
    }
}
