package com.ecoinvest.soa.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

class AppConfigTest {

    @Test
    void restTemplateBeanIsConfigured() {
        RestTemplate restTemplate = new AppConfig().restTemplate(new RestTemplateBuilder());
        assertThat(restTemplate).isNotNull();
    }
}
