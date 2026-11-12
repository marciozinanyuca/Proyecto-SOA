package com.ecoinvest.soa.client;

import com.ecoinvest.soa.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ecoinvest.soa.client.SenaceTransport.*;
import static com.ecoinvest.soa.snapshot.ResultadoLkg.Origen.*;

/** Fixtures artificiales; cliente/mapper/repositorio reales, transporte sin red; sin AOP aqui. */
class SenaceSnapshotCoordinatorTest {
    @TempDir Path directory;
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final SenaceTransport transport = mock(SenaceTransport.class);
    private final SenaceClient client = new SenaceClient(transport, json);
    private static final String PROJECTS = """
            [{"ID":9007199254740993,"RUC":"00000000001","ACTIVIDAD":" Minería ",
              "FECHA_INICIO":"01/02/2017","ESTADO":"Aprobado","LATITUD":-12.1234567890123456789,"LONGITUD":-77.1},
             {"ID":2,"ACTIVIDAD":"PESCA","FECHA_INICIO":"01/02/2018"},
             {"ID":3,"ACTIVIDAD":"MINERIA","FECHA_INICIO":"fecha artificial"}]
            """;
    private SnapshotProperties policy() {
        return new SnapshotProperties(directory.resolve("store"), 1024 * 1024,
                Duration.parse("PT24H"), Duration.parse("P90D"), Duration.parse("P7D"), Duration.parse("P365D"));
    }
    private JsonFileSnapshotRepository repository(Instant time) {
        return new JsonFileSnapshotRepository(policy(), new SnapshotValidator(), Clock.fixed(time, ZoneOffset.UTC));
    }
    private SenaceSnapshotCoordinator coordinator(SnapshotRepository repository, Instant time) {
        return new SenaceSnapshotCoordinator(client, repository, json, Clock.fixed(time, ZoneOffset.UTC));
    }
    private void seed() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
        try (var repository = repository(now)) {
            assertEquals(API_DIRECTA_SENACE, coordinator(repository, now).consultarProyectos("MINERIA", 2017).origen());
        }
    }

    @Test void completeSnapshotPrecedesFilteringAndKeepsPrecisionAndTemporalQuality() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
        try (var repository = repository(now)) {
            var result = coordinator(repository, now).consultarProyectos("MINERIA", 2017);
            assertEquals(API_DIRECTA_SENACE, result.origen());
            assertEquals(SnapshotRepository.WriteStatus.SAVED, result.escritura());
            assertEquals(1, result.datos().size());
            assertEquals("00000000001", result.datos().get(0).ruc());
            assertEquals(new java.math.BigDecimal("-12.1234567890123456789"), result.datos().get(0).latitud());
            assertEquals(3, repository.read(SnapshotKey.proyectos()).snapshot().recordCount());
            assertEquals(1, result.calidadSenace().conteos().fechasNoEvaluables());
            assertEquals(1, result.calidadSenace().conteos().aprobados());
            verify(transport, times(1)).consultar(Recurso.CARTERA_PROYECTOS);
        }
    }

    @Test void reconstructedRepositoryProvidesAnotherYearWithoutExtraGetDuringRecovery() {
        seed();
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(failed(Estado.ERROR_HTTP));
        try (var repository = repository(now.plusSeconds(1))) {
            var result = coordinator(repository, now.plusSeconds(1)).consultarProyectos("PESCA", 2018);
            assertEquals(RESPALDO_LOCAL, result.origen());
            assertEquals(2L, result.datos().get(0).id());
            assertEquals(now, result.respaldo().capturadoEn());
            assertEquals("ERROR_HTTP", result.intentoApi().motivo());
            verify(transport, times(2)).consultar(Recurso.CARTERA_PROYECTOS);
        }
    }

    @Test void failureWithoutSnapshotHasUnknownDataRatherThanOfficialZero() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(failed(Estado.TIMEOUT_CONEXION));
        try (var repository = repository(now)) {
            var result = coordinator(repository, now).consultarProyectos(null, null);
            assertEquals(SIN_RESPALDO, result.origen());
            assertNull(result.datos());
            assertNull(result.calidadSenace());
            assertEquals(SnapshotRepository.ReadStatus.MISSING, result.lectura());
        }
    }

    @Test void invalidAndIncompleteResponsesNeverReplaceGoodSnapshot() {
        seed();
        try (var repository = repository(now.plusSeconds(1))) {
            var coordinator = coordinator(repository, now.plusSeconds(1));
            for (var response : java.util.List.of(received("[{\"ID\":1.5}]"), received("[null]"),
                    received("{\"error\":true}"), failed(Estado.HTML_INESPERADO), failed(Estado.RESPUESTA_INCOMPLETA),
                    new RespuestaTransporte(Estado.RECIBIDO, 200, TipoContenido.JSON, "application/json", 1, null, false, "["))) {
                when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(response);
                assertEquals(RESPALDO_LOCAL, coordinator.consultarProyectos("MINERIA", 2017).origen());
                assertEquals(now, repository.read(SnapshotKey.proyectos()).snapshot().capturedAt());
            }
        }
    }

    @Test void oldWithinMaximumIsUsableButExpiredIsNot() {
        seed();
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(failed(Estado.ERROR_HTTP));
        for (int days : new int[] {2, 90, 91}) {
            Instant time = now.plus(Duration.ofDays(days));
            try (var repository = repository(time)) {
                var result = coordinator(repository, time).consultarProyectos("MINERIA", 2017);
                assertEquals(days > 90 ? SIN_RESPALDO : RESPALDO_LOCAL, result.origen());
                if (days <= 90) assertTrue(result.respaldo().antiguo());
                else assertEquals(SnapshotRepository.ReadStatus.EXPIRED, result.lectura());
            }
        }
    }

    @Test void corruptSnapshotIsUnavailable() throws Exception {
        seed();
        Files.writeString(policy().directory().resolve("senace/cartera-proyectos.json"), "{corrupto");
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(failed(Estado.ERROR_HTTP));
        try (var repository = repository(now)) {
            var result = coordinator(repository, now).consultarProyectos(null, null);
            assertEquals(SIN_RESPALDO, result.origen());
            assertEquals(SnapshotRepository.ReadStatus.INVALID, result.lectura());
        }
    }

    @Test void emptyDirectResponseDoesNotHideItselfBehindNonemptyBackup() {
        seed();
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received("[]"));
        try (var repository = repository(now.plusSeconds(1))) {
            var result = coordinator(repository, now.plusSeconds(1)).consultarProyectos(null, null);
            assertEquals(API_DIRECTA_SENACE, result.origen());
            assertTrue(result.datos().isEmpty());
            assertEquals(SnapshotRepository.WriteStatus.EMPTY_REPLACEMENT_REJECTED, result.escritura());
            assertEquals(3, repository.read(SnapshotKey.proyectos()).snapshot().recordCount());
        }
    }

    @Test void firstEmptyAndNoMatchesRemainDifferent() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received("[]"));
        try (var repository = repository(now.plusSeconds(1))) {
            var empty = coordinator(repository, now).consultarProyectos(null, null);
            assertEquals(ResultadoConsultaSenace.EstadoConsulta.VACIA, empty.calidadSenace().estadoDatos());
            assertEquals(SnapshotRepository.WriteStatus.SAVED, empty.escritura());
            when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
            var selection = coordinator(repository, now.plusSeconds(1)).consultarProyectos("PESCA", 2020);
            assertEquals(ResultadoConsultaSenace.EstadoConsulta.EXITO, selection.calidadSenace().estadoDatos());
            assertEquals(ResultadoConsultaSenace.EstadoFiltrado.SIN_COINCIDENCIAS, selection.calidadSenace().filtrado());
        }
    }

    @Test void consultantDuplicatesOriginalDatesAndOptionalFieldsSurviveFallback() {
        String rows = "[{\"RAZON_SOCIAL\":\"PRUEBA\",\"FECHA_RD\":\"original\"},"
                + "{\"RAZON_SOCIAL\":\"PRUEBA\",\"FECHA_RD\":\"original\"}]";
        when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(received(rows));
        try (var repository = repository(now)) {
            var coordinator = coordinator(repository, now);
            assertEquals(2, coordinator.consultarConsultoras().datos().size());
            when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(failed(Estado.ERROR_HTTP));
            var result = coordinator.consultarConsultoras();
            assertEquals(RESPALDO_LOCAL, result.origen());
            assertEquals(2, result.datos().size());
            assertEquals("original", result.datos().get(0).fechaRd());
            assertNull(result.datos().get(0).actividad());
        }
    }

    @Test void diskFailureDoesNotInvalidateDirectData() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
        SnapshotRepository broken = mock(SnapshotRepository.class);
        when(broken.save(any(), any(), anyBoolean(), any(), any())).thenThrow(new IllegalStateException("fallo artificial"));
        var result = coordinator(broken, now).consultarProyectos("MINERIA", 2017);
        assertEquals(API_DIRECTA_SENACE, result.origen());
        assertEquals(1, result.datos().size());
        assertEquals(SnapshotRepository.WriteStatus.IO_ERROR, result.escritura());
        verify(broken, never()).read(any());
    }

    @Test void publicClientKeepsItsOriginalBehaviorAndDoesNotUseSnapshots() {
        seed();
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(failed(Estado.ERROR_HTTP));
        assertThrows(SenaceClient.ConsultaSenaceException.class, () -> client.consultarProyectos("MINERIA", 2017));
        assertEquals(ResultadoConsultaSenace.Origen.NO_DISPONIBLE,
                client.fallbackConsultaProyectos("MINERIA", 2017, new RuntimeException()).origen());
    }

    @Test void genericStillIncludesAllYearsAndLegacyResourcesKeepTheirOpaqueObject() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(received(PROJECTS));
        var generic = client.consultarDatastream("CarteraProyectos");
        assertEquals(3, ((java.util.List<?>) generic.get("data")).size());
        assertEquals("API_DIRECTA_SENACE", generic.get("origen"));
        var legacy = java.util.Arrays.stream(Recurso.values())
                .filter(resource -> !resource.verificado()).findFirst().orElseThrow();
        when(transport.consultar(legacy)).thenReturn(received("{\"datoArtificial\":1}"));
        var result = client.consultarDatastream(legacy.nombre());
        assertInstanceOf(java.util.Map.class, result.get("data"));
        assertEquals(false, result.get("recursoValidado"));
        assertFalse(Files.exists(policy().directory()));
    }

    @Test void concurrentOlderResponseDoesNotOverwriteNewerSnapshot() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("lkg-older-artificial")) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout artificial");
                return received("[{\"ID\":1,\"FECHA_INICIO\":\"01/01/2017\"}]");
            }
            return received("[{\"ID\":2,\"FECHA_INICIO\":\"01/01/2017\"}]");
        });
        try (var repository = repository(now.plusSeconds(10))) {
            var pool = Executors.newSingleThreadExecutor(r -> new Thread(r, "lkg-older-artificial"));
            try {
                var old = pool.submit(() -> coordinator(repository, now).consultarProyectos(null, null));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(SnapshotRepository.WriteStatus.SAVED,
                        coordinator(repository, now.plusSeconds(10)).consultarProyectos(null, null).escritura());
                release.countDown();
                assertEquals(SnapshotRepository.WriteStatus.OLDER_RESPONSE_REJECTED, old.get(5, TimeUnit.SECONDS).escritura());
                assertEquals(2, repository.read(SnapshotKey.proyectos()).snapshot().payload().get(0).path("ID").intValue());
            } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
        }
    }

    private static RespuestaTransporte received(String body) {
        int size = body.getBytes(StandardCharsets.UTF_8).length;
        return new RespuestaTransporte(Estado.RECIBIDO, 200, TipoContenido.JSON, "application/json", size, (long) size, true, body);
    }
    private static RespuestaTransporte failed(Estado state) {
        return new RespuestaTransporte(state, 503, TipoContenido.DESCONOCIDO, null, 0, null, false, null);
    }
}
