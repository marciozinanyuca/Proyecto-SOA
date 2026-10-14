package com.ecoinvest.soa.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
class SenaceClientTest {

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private SenaceClient senaceClient;

    @Test
    void whenApiFails_thenFallbackSenaceReturnsLocalSqliteProjects() {
        when(restTemplate.getForObject(anyString(), eq(List.class)))
                .thenThrow(new ResourceAccessException("Conexión fallida a SENACE"));

        List<Map<String, Object>> proyectos = senaceClient.obtenerProyectos("Minería", 2024);

        assertThat(proyectos).isNotEmpty();
        assertThat(proyectos.get(0).get("origen")).isEqualTo("FALLBACK_SQLITE");
        assertThat(proyectos.get(0).get("sector")).isEqualTo("Minería");
        assertThat(proyectos.get(0).get("anio")).isEqualTo(2024);
    }

    @Test
    void directFallbackSenace_returnsExpectedStructure() {
        List<Map<String, Object>> fallback = senaceClient.fallbackSenace("Electricidad", 2023, new RuntimeException("Error simulado"));

        assertThat(fallback).isNotEmpty();
        assertThat(fallback).allMatch(p -> "FALLBACK_SQLITE".equals(p.get("origen")));
        assertThat(fallback).allMatch(p -> "Electricidad".equals(p.get("sector")));
    }
}
