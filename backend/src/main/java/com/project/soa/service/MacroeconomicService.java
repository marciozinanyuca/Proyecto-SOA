package com.project.soa.service;

import com.project.soa.client.BcrpClient;
import com.project.soa.dto.MacroeconomicDataResponse;
import com.project.soa.dto.MacroeconomicObservation;
import com.project.soa.dto.bcrp.BcrpPeriodDto;
import com.project.soa.dto.bcrp.BcrpResponseDto;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MacroeconomicService {

    private static final String SOURCE = "BCRP - Banco Central de Reserva del Perú";

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{1,2})(?:-(\\d{1,2}))?$");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$");
    private static final Pattern SLASH_YEAR_MONTH_PATTERN = Pattern.compile("^(\\d{1,2})/(\\d{4})$");
    private static final Pattern SLASH_ISO_DATE_PATTERN = Pattern.compile("^(\\d{4})/(\\d{1,2})(?:/(\\d{1,2}))?$");

    private final BcrpClient bcrpClient;

    public MacroeconomicService(BcrpClient bcrpClient) {
        this.bcrpClient = bcrpClient;
    }

    public MacroeconomicDataResponse getMacroeconomicData(
            String indicatorCode,
            String startDate,
            String endDate) {
        String formattedStartDate = formatDateForBcrp(startDate);
        String formattedEndDate = formatDateForBcrp(endDate);
        BcrpResponseDto bcrpResponse = bcrpClient.getMacroeconomicIndicator(indicatorCode, formattedStartDate, formattedEndDate);
        if (bcrpResponse == null || bcrpResponse.getConfig() == null) {
            throw new IllegalStateException("BCRP devolvió una respuesta sin configuración del indicador");
        }

        List<MacroeconomicObservation> observations = mapPeriods(bcrpResponse.getPeriods());
        String indicatorName = bcrpResponse.getConfig().getTitle();
        if (indicatorName == null || indicatorName.isBlank()) {
            throw new IllegalStateException("BCRP devolvió una respuesta sin título para el indicador");
        }

        MacroeconomicDataResponse response = new MacroeconomicDataResponse();
        response.setSource(SOURCE);
        response.setIndicatorCode(indicatorCode);
        response.setIndicatorName(indicatorName);
        response.setUnit(getIndicatorUnit(indicatorCode));
        response.setValues(observations);
        return response;
    }

    private List<MacroeconomicObservation> mapPeriods(List<BcrpPeriodDto> periods) {
        if (periods == null) {
            throw new IllegalStateException("BCRP devolvió una respuesta sin períodos");
        }

        List<MacroeconomicObservation> observations = new ArrayList<>(periods.size());
        Double previousValue = null;
        for (BcrpPeriodDto period : periods) {
            if (period == null || period.getValues() == null || period.getValues().isEmpty()) {
                throw new IllegalStateException("BCRP devolvió un período sin valores");
            }

            String rawValue = period.getValues().get(0);
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalStateException("BCRP devolvió un valor vacío para un período");
            }

            double value;
            try {
                value = Double.parseDouble(rawValue.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("BCRP devolvió un valor no numérico: " + rawValue, exception);
            }

            MacroeconomicObservation observation = new MacroeconomicObservation();
            observation.setDate(parsePeriodDate(period.getName()));
            observation.setValue(value);
            if (previousValue != null && previousValue != 0) {
                double variationPercent = ((value - previousValue) / previousValue) * 100;
                observation.setVariationPercent(Math.round(variationPercent * 100.0) / 100.0);
            }
            observations.add(observation);
            previousValue = value;
        }
        return observations;
    }

    private LocalDate parsePeriodDate(String periodName) {
        if (periodName == null || periodName.isBlank()) {
            throw new IllegalStateException("BCRP devolvió un período sin nombre");
        }

        String normalizedName = periodName.trim();
        try {
            return YearMonth.parse(normalizedName).atDay(1);
        } catch (DateTimeParseException ignored) {
            // BCRP también entrega períodos mensuales como "2020-1" y "Ene.2020".
        }

        String[] numericParts = normalizedName.split("-");
        if (numericParts.length == 2) {
            try {
                return YearMonth.of(
                        Integer.parseInt(numericParts[0]),
                        Integer.parseInt(numericParts[1])).atDay(1);
            } catch (NumberFormatException | DateTimeException exception) {
                throw new IllegalStateException("BCRP devolvió un período con formato inválido: " + periodName, exception);
            }
        }

        String[] spanishParts = normalizedName.replace(".", " ").trim().split("\\s+");
        if (spanishParts.length == 2) {
            int month = getSpanishMonth(spanishParts[0]);
            try {
                return YearMonth.of(Integer.parseInt(spanishParts[1]), month).atDay(1);
            } catch (NumberFormatException | DateTimeException exception) {
                throw new IllegalStateException("BCRP devolvió un período con formato inválido: " + periodName, exception);
            }
        }

        throw new IllegalStateException("BCRP devolvió un período con formato no reconocido: " + periodName);
    }

    private int getSpanishMonth(String monthName) {
        return switch (monthName.toLowerCase(Locale.ROOT)) {
            case "ene", "enero" -> 1;
            case "feb", "febrero" -> 2;
            case "mar", "marzo" -> 3;
            case "abr", "abril" -> 4;
            case "may", "mayo" -> 5;
            case "jun", "junio" -> 6;
            case "jul", "julio" -> 7;
            case "ago", "agosto" -> 8;
            case "sep", "set", "septiembre", "setiembre" -> 9;
            case "oct", "octubre" -> 10;
            case "nov", "noviembre" -> 11;
            case "dic", "diciembre" -> 12;
            default -> throw new IllegalStateException("BCRP devolvió un mes no reconocido: " + monthName);
        };
    }

    private String getIndicatorUnit(String code) {
        return switch (code) {
            case "INFLATION", "INTEREST_RATE" -> "%";
            case "GDP" -> "Millones USD";
            case "EXCHANGE_RATE" -> "PEN/USD";
            default -> "Unidad";
        };
    }

    /**
     * Convierte una fecha en formato estándar (ej. YYYY-MM-DD, DD/MM/YYYY, YYYY-MM)
     * al formato estrictamente exigido por el BCRP: YYYY-M (ej. 2025-1).
     *
     * @param dateStr Fecha recibida en formato de texto.
     * @return Cadena formateada como YYYY-M para la API del BCRP.
     */
    private String formatDateForBcrp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("La fecha para BCRP no puede ser nula ni vacía");
        }

        String normalized = dateStr.trim();

        try {
            // Formato ISO: YYYY-MM-DD, YYYY-M-D, YYYY-MM o YYYY-M
            Matcher isoMatcher = ISO_DATE_PATTERN.matcher(normalized);
            if (isoMatcher.matches()) {
                int year = Integer.parseInt(isoMatcher.group(1));
                int month = Integer.parseInt(isoMatcher.group(2));
                String dayStr = isoMatcher.group(3);
                if (dayStr != null) {
                    LocalDate date = LocalDate.of(year, month, Integer.parseInt(dayStr));
                    return date.getYear() + "-" + date.getMonthValue();
                }
                YearMonth ym = YearMonth.of(year, month);
                return ym.getYear() + "-" + ym.getMonthValue();
            }

            // Formato DD/MM/YYYY o D/M/YYYY
            Matcher slashMatcher = SLASH_DATE_PATTERN.matcher(normalized);
            if (slashMatcher.matches()) {
                int day = Integer.parseInt(slashMatcher.group(1));
                int month = Integer.parseInt(slashMatcher.group(2));
                int year = Integer.parseInt(slashMatcher.group(3));
                LocalDate date = LocalDate.of(year, month, day);
                return date.getYear() + "-" + date.getMonthValue();
            }

            // Formato MM/YYYY o M/YYYY
            Matcher slashYearMonthMatcher = SLASH_YEAR_MONTH_PATTERN.matcher(normalized);
            if (slashYearMonthMatcher.matches()) {
                int month = Integer.parseInt(slashYearMonthMatcher.group(1));
                int year = Integer.parseInt(slashYearMonthMatcher.group(2));
                YearMonth ym = YearMonth.of(year, month);
                return ym.getYear() + "-" + ym.getMonthValue();
            }

            // Formato ISO con barras: YYYY/MM/DD o YYYY/MM
            Matcher slashIsoMatcher = SLASH_ISO_DATE_PATTERN.matcher(normalized);
            if (slashIsoMatcher.matches()) {
                int year = Integer.parseInt(slashIsoMatcher.group(1));
                int month = Integer.parseInt(slashIsoMatcher.group(2));
                String dayStr = slashIsoMatcher.group(3);
                if (dayStr != null) {
                    LocalDate date = LocalDate.of(year, month, Integer.parseInt(dayStr));
                    return date.getYear() + "-" + date.getMonthValue();
                }
                YearMonth ym = YearMonth.of(year, month);
                return ym.getYear() + "-" + ym.getMonthValue();
            }

            // Fallback con LocalDate para formatos estándar ISO adicionales
            LocalDate parsedDate = LocalDate.parse(normalized);
            return parsedDate.getYear() + "-" + parsedDate.getMonthValue();
        } catch (DateTimeException | NumberFormatException exception) {
            throw new IllegalArgumentException("Fecha no válida para BCRP: " + dateStr, exception);
        }
    }
}
