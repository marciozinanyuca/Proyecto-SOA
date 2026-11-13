package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** Inspeccion de ConfigData sin crear DataSource, usuarios o infraestructura externa. */
class RenderDeploymentConfigurationTest {
    @Test
    void profileUsesAuthenticationPersistenceSecureCookiesAndNoLkg() throws Exception {
        var profile = new YamlPropertySourceLoader()
                .load("render", new ClassPathResource("application-render.yml")).get(0);
        assertThat(profile.getProperty("spring.autoconfigure.exclude")).isNull();
        assertThat(profile.getProperty("spring.datasource.url")).isEqualTo("jdbc:sqlite:${ECOINVEST_AUTH_DB_PATH}");
        assertThat(profile.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(profile.getProperty("spring.sql.init.schema-locations"))
                .isEqualTo("classpath:db/render-auth-schema.sql");
        assertThat(profile.getProperty("server.servlet.session.cookie.secure")).isEqualTo(true);
        assertThat(profile.getProperty("server.servlet.session.cookie.http-only")).isEqualTo(true);
        assertThat(profile.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(profile.getProperty("server.address")).isEqualTo("0.0.0.0");
        assertThat(profile.getProperty("ecoinvest.snapshots.enabled")).isEqualTo(false);
        assertThat(new ClassPathResource("db/render-auth-schema.sql").exists()).isTrue();
    }
}
