package com.project.soa.client;

import com.project.soa.dto.bcrp.BcrpResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BcrpClient {

    private static final Pattern YEAR_FIRST_PATTERN = Pattern.compile("^(\\d{4})[-/](\\d{1,2})(?:[-/](\\d{1,2}))?$");
    private static final Pattern YEAR_LAST_PATTERN = Pattern.compile("^(?:(\\d{1,2})[-/])?(\\d{1,2})[-/](\\d{4})$");

    @Value("${bcrp.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BcrpClient(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
    }

    public BcrpResponseDto getMacroeconomicIndicator(String indicatorCode, String startDate, String endDate) {
        startDate = sanitizeDate(startDate);
        endDate = sanitizeDate(endDate);

        String url = baseUrl + indicatorCode + "/json/" + startDate + "/" + endDate + "/esp";
        String rawResponse = restTemplate.getForObject(url, String.class);

        System.out.println("URL BCRP: " + url);
        System.out.println("RESPUESTA BCRP: " + rawResponse);

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("BCRP devolvió una respuesta vacía");
        }
        return objectMapper.readValue(rawResponse, BcrpResponseDto.class);
    }

    private String sanitizeDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("La fecha no puede ser nula ni vacía");
        }

        String trimmed = dateStr.trim();

        Matcher yearFirstMatcher = YEAR_FIRST_PATTERN.matcher(trimmed);
        if (yearFirstMatcher.matches()) {
            int year = Integer.parseInt(yearFirstMatcher.group(1));
            int month = Integer.parseInt(yearFirstMatcher.group(2));
            validateMonth(month);
            return year + "-" + month;
        }

        Matcher yearLastMatcher = YEAR_LAST_PATTERN.matcher(trimmed);
        if (yearLastMatcher.matches()) {
            int month = Integer.parseInt(yearLastMatcher.group(2));
            int year = Integer.parseInt(yearLastMatcher.group(3));
            validateMonth(month);
            return year + "-" + month;
        }

        throw new IllegalArgumentException("Formato de fecha no soportado: " + dateStr);
    }

    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Mes inválido: " + month);
        }
    }
}
