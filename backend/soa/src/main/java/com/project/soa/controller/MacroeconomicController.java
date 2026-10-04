package com.project.soa.controller;

import com.project.soa.api.MacroeconomaApi;
import com.project.soa.dto.MacroeconomicDataResponse;
import com.project.soa.dto.MacroeconomicObservation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
public class MacroeconomicController implements MacroeconomaApi {

    @Override
    public ResponseEntity<MacroeconomicDataResponse> getMacroeconomicData(
            String indicatorCode,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDate parsedStartDate = startDate != null ? startDate : LocalDate.now();
        LocalDate parsedEndDate = endDate != null ? endDate : LocalDate.now().plusYears(1);

        MacroeconomicDataResponse response = new MacroeconomicDataResponse();
        response.setSource("BCRP - Banco Central de Reserva del Perú");
        response.setIndicatorCode(indicatorCode);
        response.setIndicatorName(getIndicatorName(indicatorCode));
        response.setUnit(getIndicatorUnit(indicatorCode));
        response.setValues(generateSimulatedData(parsedStartDate, parsedEndDate, indicatorCode));

        return ResponseEntity.ok(response);
    }

    private String getIndicatorName(String code) {
        return switch (code) {
            case "INFLATION" -> "Tasa de Inflación";
            case "GDP" -> "Producto Bruto Interno";
            case "EXCHANGE_RATE" -> "Tipo de Cambio";
            case "INTEREST_RATE" -> "Tasa de Interés";
            default -> "Indicador Desconocido";
        };
    }

    private String getIndicatorUnit(String code) {
        return switch (code) {
            case "INFLATION" -> "%";
            case "GDP" -> "Millones USD";
            case "EXCHANGE_RATE" -> "PEN/USD";
            case "INTEREST_RATE" -> "%";
            default -> "Unidad";
        };
    }

    private List<MacroeconomicObservation> generateSimulatedData(
            LocalDate startDate,
            LocalDate endDate,
            String indicatorCode) {

        List<MacroeconomicObservation> observations = new ArrayList<>();
        LocalDate current = startDate;
        double baseValue = getBaseValue(indicatorCode);

        while (!current.isAfter(endDate)) {
            MacroeconomicObservation observation = new MacroeconomicObservation();
            observation.setDate(current);
            double variance = (Math.random() - 0.5) * 2;
            double value = baseValue + variance;
            observation.setValue(Math.max(0, value));

            if (!observations.isEmpty()) {
                double prevValue = observations.get(observations.size() - 1).getValue();
                double variation = ((value - prevValue) / prevValue) * 100;
                observation.setVariationPercent(Math.round(variation * 100.0) / 100.0);
            }

            observations.add(observation);
            current = current.plusMonths(1);
        }

        return observations;
    }

    private double getBaseValue(String code) {
        return switch (code) {
            case "INFLATION" -> 3.5;
            case "GDP" -> 420000.0;
            case "EXCHANGE_RATE" -> 3.75;
            case "INTEREST_RATE" -> 5.25;
            default -> 100.0;
        };
    }
}
