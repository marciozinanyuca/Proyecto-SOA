package com.ecoinvest.soa.config;

import com.ecoinvest.soa.client.*;
import com.ecoinvest.soa.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Contexto de configuracion sin Boot, sockets ni persistencia JPA; todo payload es artificial. */
class LkgConfigurationTest {
    @TempDir Path directory;
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(Dependencies.class, SnapshotConfiguration.class)
                .withPropertyValues("ECOINVEST_SNAPSHOT_DIR=" + directory.resolve("store"));
    }
    private String[] ages() {
        return new String[] {"ecoinvest.snapshots.senace-stale-after=PT24H", "ecoinvest.snapshots.senace-max-age=P90D",
                "ecoinvest.snapshots.bcrp-stale-after=P7D", "ecoinvest.snapshots.bcrp-max-age=P365D"};
    }

    @Test void missingFlagAndFalseKeepAllLkgBeansAbsent() {
        for (String enabled : new String[] {"", "ecoinvest.snapshots.enabled=false"}) {
            var runner = enabled.isEmpty() ? context() : context().withPropertyValues(enabled);
            runner.run(ctx -> {
                assertNull(ctx.getStartupFailure());
                assertTrue(ctx.getBeansOfType(SnapshotRepository.class).isEmpty());
                assertTrue(ctx.getBeansOfType(SenaceSnapshotCoordinator.class).isEmpty());
                assertTrue(ctx.getBeansOfType(BcrpSnapshotCoordinator.class).isEmpty());
                assertTrue(ctx.getBeansOfType(SnapshotStatusService.class).isEmpty());
                assertFalse(Files.exists(directory.resolve("store")));
            });
        }
    }

    @Test void explicitEnableRequiresExplicitAgePolicy() {
        context().withPropertyValues("ecoinvest.snapshots.enabled=true").run(ctx -> {
            assertNotNull(ctx.getStartupFailure());
            Throwable root = ctx.getStartupFailure();
            while (root.getCause() != null) root = root.getCause();
            assertEquals("POLITICA_ANTIGUEDAD_REQUERIDA", root.getMessage());
            assertFalse(Files.exists(directory.resolve("store")));
        });
    }

    @Test void approvedPolicyRegistersObservedRepositoryAndCoordinatorsWithoutIo() {
        context().withPropertyValues("ecoinvest.snapshots.enabled=true").withPropertyValues(ages()).run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertInstanceOf(ObservedSnapshotRepository.class, ctx.getBean(SnapshotRepository.class));
            assertNotNull(ctx.getBean(SenaceSnapshotCoordinator.class));
            assertNotNull(ctx.getBean(BcrpSnapshotCoordinator.class));
            var policy = ctx.getBean(SnapshotProperties.class);
            assertEquals(Duration.ofDays(90), policy.senaceMaxAge());
            assertEquals(Duration.ofDays(365), policy.bcrpMaxAge());
            assertEquals(Duration.ofDays(7), policy.bcrpStaleAfter());
            assertEquals("HABILITADO_SIN_VERIFICACION", ctx.getBean(SnapshotStatusService.class).state(null));
            assertFalse(Files.exists(policy.directory()));
            verifyNoInteractions(ctx.getBean(SenaceClient.class), ctx.getBean(BcrpClient.class));
        });
    }

    @Test void observerTracksFailuresRecoveryAndDoesNotReadForMonitor() throws Exception {
        var policy = new SnapshotProperties(directory, 16384, Duration.ofHours(24), Duration.ofDays(90),
                Duration.ofDays(7), Duration.ofDays(365));
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);
        var status = new SnapshotStatusService(policy, clock);
        SnapshotRepository delegate = mock(SnapshotRepository.class);
        when(delegate.save(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(SnapshotRepository.WriteStatus.IO_ERROR, SnapshotRepository.WriteStatus.SAVED);
        try (var observed = new ObservedSnapshotRepository(delegate, status)) {
            var payload = new ObjectMapper().readTree("[{\"ID\":1}]");
            observed.save(SnapshotKey.proyectos(), payload, true, clock.instant(), clock.instant());
            assertEquals("CON_INCIDENCIAS", status.state(SnapshotKey.Source.SENACE));
            observed.save(SnapshotKey.proyectos(), payload, true, clock.instant(), clock.instant());
            assertEquals("OPERATIVO", status.state(SnapshotKey.Source.SENACE));
            var data = status.snapshot();
            assertEquals("ULTIMAS_OBSERVACIONES_DEL_PROCESO", data.get("alcance"));
            assertFalse(data.toString().contains("payload"));
            verify(delegate, never()).read(any());
        }
    }

    @Test void missingReadDoesNotClaimOperationalBackupAndRegistryIsBounded() {
        var policy = new SnapshotProperties(directory, 16384, Duration.ofHours(24), Duration.ofDays(90),
                Duration.ofDays(7), Duration.ofDays(365));
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);
        var status = new SnapshotStatusService(policy, clock);
        for (int year = 1900; year < 2100; year++) {
            status.read(SnapshotKey.bcrp("PM04973AA", year),
                    new SnapshotRepository.ReadResult(SnapshotRepository.ReadStatus.MISSING, null, null, false));
        }
        assertEquals("HABILITADO_SIN_VERIFICACION", status.state(null));
        assertEquals(128, ((java.util.List<?>) status.snapshot().get("observaciones")).size());
        assertFalse(Files.exists(directory.resolve("bcrp")));
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean SenaceClient senace() { return mock(SenaceClient.class); }
        @Bean BcrpClient bcrp() { return mock(BcrpClient.class); }
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean("snapshotClock") Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
