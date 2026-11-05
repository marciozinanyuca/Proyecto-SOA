package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecoinvest.model.DatastreamSenaceResponse;
import com.ecoinvest.model.IndicadorBcrp;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class AppConfigTest {

    @Test
    void bootRegistersNullableModuleWithoutDatabaseOrDecimalRegression() {
        // Solo Jackson y AppConfig: sin Spring Boot completo, scanning, servidor o SQLite.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(AppConfig.class)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanNamesForType(javax.sql.DataSource.class)).isEmpty();
                    ObjectMapper jackson = context.getBean(ObjectMapper.class);
                    DatastreamSenaceResponse response = new DatastreamSenaceResponse();
                    response.data(List.of(Map.of("campo", "FIXTURE ARTIFICIAL")));
                    JsonNode array = jackson.readTree(jackson.writeValueAsString(response));
                    assertThat(array.get("data").isArray()).isTrue();
                    assertThat(array.get("data").get(0).get("campo").asText()).isEqualTo("FIXTURE ARTIFICIAL");
                    response.data(Map.of("campo", "FIXTURE ARTIFICIAL"));
                    JsonNode object = jackson.readTree(jackson.writeValueAsString(response));
                    assertThat(object.get("data").get("campo").asText()).isEqualTo("FIXTURE ARTIFICIAL");
                    response.data(null);
                    JsonNode absent = jackson.readTree(jackson.writeValueAsString(response));
                    assertThat(absent.has("data")).isTrue();
                    assertThat(absent.get("data").isNull()).isTrue();
                    BigDecimal value = new BigDecimal("0.12345678901234567890123456789");
                    IndicadorBcrp indicador = new IndicadorBcrp();
                    indicador.setValor(value);
                    String serialized = jackson.writeValueAsString(indicador);
                    JsonNode economic = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                            .readTree(serialized);
                    assertThat(economic.get("valor").decimalValue()).isEqualByComparingTo(value);
                });
    }

    @Test
    void restTemplateBeanIsConfigured() {
        RestTemplate restTemplate = new AppConfig().restTemplate(new RestTemplateBuilder());
        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "connectTimeout")).isEqualTo(5000);
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "readTimeout")).isEqualTo(5000);
    }
}
