package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecoinvest.model.DashboardResponse;
import com.ecoinvest.model.IndicadorBcrp;
import com.ecoinvest.model.LkgMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** Inspeccion de ConfigData sin crear DataSource, usuarios o infraestructura externa. */
class RenderDeploymentConfigurationTest {
    @Test
    void disabledLkgOmitsAbsentBcrpMetadataWithoutLosingEconomicValue() throws Exception {
        var mapper = new ObjectMapper();
        var indicator = new IndicadorBcrp().origen("API_DIRECTA_BCRP").valor(new BigDecimal("-2.448"));
        var dashboard = new DashboardResponse().indicadorBcrp(indicator);
        var serialized = mapper.readTree(mapper.writeValueAsString(dashboard)).get("indicadorBcrp");
        assertThat(serialized.has("lkg")).isFalse();
        assertThat(serialized.get("origen").asText()).isEqualTo("API_DIRECTA_BCRP");
        assertThat(serialized.get("valor").decimalValue()).isEqualByComparingTo("-2.448");
        indicator.setLkg(new LkgMetadata());
        assertThat(mapper.readTree(mapper.writeValueAsString(indicator)).has("lkg")).isTrue();
        indicator.setLkg(null);
        indicator.setValor(null);
        var unavailable = mapper.readTree(mapper.writeValueAsString(indicator));
        assertThat(unavailable.has("lkg")).isFalse();
        assertThat(unavailable.has("valor")).isFalse();
    }

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
