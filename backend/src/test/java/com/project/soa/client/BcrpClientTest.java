package com.project.soa.client;

import com.project.soa.dto.bcrp.BcrpResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BcrpClientTest {

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void testGetMacroeconomicIndicator() throws Exception {
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        BcrpClient client = new BcrpClient(restTemplateBuilder, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", "https://estadisticas.bcrp.gob.pe/estadisticas/series/api/");

        BcrpResponseDto mockResponse = new BcrpResponseDto();
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"periods\":[]}");
        when(objectMapper.readValue(anyString(), eq(BcrpResponseDto.class))).thenReturn(mockResponse);

        BcrpResponseDto response = client.getMacroeconomicIndicator("PN01288PM", "2020-01", "2020-12");

        assertNotNull(response);
        assertEquals(mockResponse, response);
    }
}
