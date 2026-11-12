package com.ecoinvest.soa.snapshot;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ecoinvest.soa.client.SenaceClient;
import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.SenaceSnapshotCoordinator;
import com.ecoinvest.soa.client.BcrpSnapshotCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Opt-in: sin activacion por defecto. Beans reciben los proxies de clientes administrados por Spring. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ecoinvest.snapshots.enabled", havingValue = "true")
public class SnapshotConfiguration {
    @Bean
    SnapshotProperties snapshotProperties(Environment env) {
        return new SnapshotProperties(Path.of(env.getProperty("ECOINVEST_SNAPSHOT_DIR", "snapshots")),
                env.getProperty("ecoinvest.snapshots.max-file-bytes", Long.class, 16L * 1024 * 1024),
                age(env, "senace-stale-after"), age(env, "senace-max-age"),
                age(env, "bcrp-stale-after"), age(env, "bcrp-max-age"));
    }

    private static Duration age(Environment env, String key) {
        String value = env.getProperty("ecoinvest.snapshots." + key);
        if (value == null) throw new IllegalArgumentException("POLITICA_ANTIGUEDAD_REQUERIDA");
        try { return Duration.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("POLITICA_ANTIGUEDAD_INVALIDA"); }
    }

    @Bean SnapshotValidator snapshotValidator() { return new SnapshotValidator(); }
    @Bean("snapshotClock")
    @ConditionalOnMissingBean(name = "snapshotClock")
    Clock snapshotClock() { return Clock.systemUTC(); }

    @Bean
    SnapshotStatusService snapshotStatusService(SnapshotProperties properties, @Qualifier("snapshotClock") Clock clock) {
        return new SnapshotStatusService(properties, clock);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SnapshotRepository.class)
    SnapshotRepository snapshotRepository(SnapshotProperties properties, SnapshotValidator validator,
            SnapshotStatusService status, @Qualifier("snapshotClock") Clock clock) {
        return new ObservedSnapshotRepository(new JsonFileSnapshotRepository(properties, validator, clock), status);
    }

    @Bean
    SenaceSnapshotCoordinator senaceSnapshotCoordinator(SenaceClient client, SnapshotRepository repository,
            ObjectMapper json, @Qualifier("snapshotClock") Clock clock) {
        return new SenaceSnapshotCoordinator(client, repository, json, clock);
    }

    @Bean
    BcrpSnapshotCoordinator bcrpSnapshotCoordinator(BcrpClient client, SnapshotRepository repository,
            @Qualifier("snapshotClock") Clock clock) {
        return new BcrpSnapshotCoordinator(client, repository, clock);
    }
}
