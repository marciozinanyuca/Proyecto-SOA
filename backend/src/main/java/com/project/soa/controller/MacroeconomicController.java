package com.project.soa.controller;

import com.project.soa.api.MacroeconomaApi;
import com.project.soa.dto.MacroeconomicDataResponse;
import com.project.soa.service.MacroeconomicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

@RestController
public class MacroeconomicController implements MacroeconomaApi {

    private final MacroeconomicService macroeconomicService;

    public MacroeconomicController(MacroeconomicService macroeconomicService) {
        this.macroeconomicService = macroeconomicService;
    }

    @Override
    public ResponseEntity<MacroeconomicDataResponse> getMacroeconomicData(
            String indicatorCode,
            LocalDate startDate,
            LocalDate endDate) {
        String formattedStartDate = YearMonth.from(Objects.requireNonNull(startDate, "startDate is required")).toString();
        String formattedEndDate = YearMonth.from(Objects.requireNonNull(endDate, "endDate is required")).toString();

        return ResponseEntity.ok(
                macroeconomicService.getMacroeconomicData(indicatorCode, formattedStartDate, formattedEndDate));
    }
}
