package com.project.soa.controller;

import com.project.soa.api.RiesgoEsgApi;
import com.project.soa.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
public class EsgRiskController implements RiesgoEsgApi {

    @Override
    public ResponseEntity<EsgRiskResponse> calculateEsgRisk(EsgRiskRequest request) {
        EsgRiskResponse response = new EsgRiskResponse();
        response.setCompanyId(request.getCompanyId());
        response.setCalculatedAt(OffsetDateTime.now());

        String indicatorCode = request.getIndicatorCode().getValue();
        double macroScore = calculateMacroeconomicScore(indicatorCode);
        double envScore = calculateEnvironmentalScore(request.getIncludeResolvedInfractions() != null && request.getIncludeResolvedInfractions());

        double combinedScore = (macroScore * 0.4) + (envScore * 0.6);
        response.setScore(Math.round(combinedScore * 100.0) / 100.0);
        response.setLevel(getEsgRiskLevel(combinedScore));
        response.setFactors(createEsgRiskFactors(indicatorCode, macroScore, envScore));
        response.setSummary(generateSummary(response.getLevel().toString(), combinedScore));

        return ResponseEntity.ok(response);
    }

    private double calculateMacroeconomicScore(String indicatorCode) {
        return switch (indicatorCode) {
            case "INFLATION" -> 65.0;
            case "GDP" -> 45.0;
            case "EXCHANGE_RATE" -> 55.0;
            case "INTEREST_RATE" -> 50.0;
            default -> 50.0;
        };
    }

    private double calculateEnvironmentalScore(boolean includeResolved) {
        return includeResolved ? 72.0 : 68.0;
    }

    private EsgRiskResponse.LevelEnum getEsgRiskLevel(double score) {
        if (score >= 80) return EsgRiskResponse.LevelEnum.CRITICAL;
        if (score >= 60) return EsgRiskResponse.LevelEnum.HIGH;
        if (score >= 40) return EsgRiskResponse.LevelEnum.MODERATE;
        return EsgRiskResponse.LevelEnum.LOW;
    }

    private EsgRiskFactors createEsgRiskFactors(String indicatorCode, double macroScore, double envScore) {
        EsgRiskFactors factors = new EsgRiskFactors();

        MacroeconomicRiskFactor macroFactor = new MacroeconomicRiskFactor();
        macroFactor.setIndicatorCode(indicatorCode);
        macroFactor.setIndicatorValue(getIndicatorValue(indicatorCode));
        macroFactor.setContribution(40.0);
        factors.setMacroeconomic(macroFactor);

        EnvironmentalRiskFactor envFactor = new EnvironmentalRiskFactor();
        envFactor.setInfractionCount(3);
        envFactor.setHighestSeverity(EnvironmentalRiskFactor.HighestSeverityEnum.CRITICAL);
        envFactor.setTotalFineAmount(195000.0);
        envFactor.setContribution(60.0);
        factors.setEnvironmental(envFactor);

        return factors;
    }

    private double getIndicatorValue(String indicatorCode) {
        return switch (indicatorCode) {
            case "INFLATION" -> 3.75;
            case "GDP" -> 420000.0;
            case "EXCHANGE_RATE" -> 3.82;
            case "INTEREST_RATE" -> 5.50;
            default -> 100.0;
        };
    }

    private String generateSummary(String level, double score) {
        return "La empresa presenta un riesgo ESG " + level.toLowerCase() +
                " con una puntuación de " + score +
                ". Se recomienda implementar medidas correctivas en gestión ambiental.";
    }
}

