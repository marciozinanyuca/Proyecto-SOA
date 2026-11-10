package com.ecoinvest.soa.config;

import java.time.Duration;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    // Soporte del tipo generado por OpenAPI para JSON arbitrario y null explicito.
    // Boot registra los beans Module en su ObjectMapper; no altera precision o transporte.
    @Bean
    public JsonNullableModule jsonNullableModule() {
        return new JsonNullableModule();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // Conservar el transporte anterior aunque SENACE incorpore HttpClient 5.
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
