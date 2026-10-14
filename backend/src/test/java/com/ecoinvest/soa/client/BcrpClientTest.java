package com.ecoinvest.soa.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
class BcrpClientTest {

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private BcrpClient bcrpClient;

    @Test
    void whenApiFails_thenFallbackBcrpReturnsLocalSqliteData() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Conexión rechazada"));

        Map<String, Object> result = bcrpClient.consultarBcrp();

        assertThat(result).isNotNull();
        assertThat(result.get("origen")).isEqualTo("FALLBACK_SQLITE");
        assertThat(result.get("serie")).isEqualTo("PM05141AA");
    }

    @Test
    void directFallbackBcrp_returnsExpectedStructure() {
        Map<String, Object> fallback = bcrpClient.fallbackBcrp(new RuntimeException("Simulated error"));

        assertThat(fallback).isNotNull();
        assertThat(fallback.get("origen")).isEqualTo("FALLBACK_SQLITE");
        assertThat(fallback.get("serie")).isEqualTo("PM05141AA");
    }
}
