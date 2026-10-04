package com.project.soa.controller;

import com.project.soa.api.InfraccionesAmbientalesApi;
import com.project.soa.dto.EnvironmentalInfraction;
import com.project.soa.dto.EnvironmentalInfractionsResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
public class EnvironmentalInfractionsController implements InfraccionesAmbientalesApi {

    @Override
    public ResponseEntity<EnvironmentalInfractionsResponse> getEnvironmentalInfractions(
            String companyId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        EnvironmentalInfractionsResponse response = new EnvironmentalInfractionsResponse();
        response.setSource("OEFA - Organismo de Evaluación y Fiscalización Ambiental");
        response.setCompanyId(companyId);

        List<EnvironmentalInfraction> infractions = generateSimulatedInfractions(companyId);
        response.setInfractions(infractions);
        response.setTotal(infractions.size());

        return ResponseEntity.ok(response);
    }

    private List<EnvironmentalInfraction> generateSimulatedInfractions(String companyId) {
        List<EnvironmentalInfraction> infractions = new ArrayList<>();

        EnvironmentalInfraction infraction1 = new EnvironmentalInfraction();
        infraction1.setInfractionId("INF-" + companyId + "-001");
        infraction1.setDescription("Emisión de gases contaminantes fuera de los límites permitidos");
        infraction1.setCategory("Aire");
        infraction1.setSeverity(EnvironmentalInfraction.SeverityEnum.HIGH);
        infraction1.setStatus(EnvironmentalInfraction.StatusEnum.CONFIRMED);
        infraction1.setFineAmount(50000.0);
        infraction1.setCurrency("PEN");
        infraction1.setReportedAt(java.time.LocalDate.of(2024, 6, 15));
        infractions.add(infraction1);

        EnvironmentalInfraction infraction2 = new EnvironmentalInfraction();
        infraction2.setInfractionId("INF-" + companyId + "-002");
        infraction2.setDescription("Vertimiento de residuos líquidos sin tratamiento adecuado");
        infraction2.setCategory("Agua");
        infraction2.setSeverity(EnvironmentalInfraction.SeverityEnum.CRITICAL);
        infraction2.setStatus(EnvironmentalInfraction.StatusEnum.UNDER_INVESTIGATION);
        infraction2.setFineAmount(120000.0);
        infraction2.setCurrency("PEN");
        infraction2.setReportedAt(java.time.LocalDate.of(2024, 8, 22));
        infractions.add(infraction2);

        EnvironmentalInfraction infraction3 = new EnvironmentalInfraction();
        infraction3.setInfractionId("INF-" + companyId + "-003");
        infraction3.setDescription("Gestión inadecuada de residuos peligrosos");
        infraction3.setCategory("Residuos");
        infraction3.setSeverity(EnvironmentalInfraction.SeverityEnum.MEDIUM);
        infraction3.setStatus(EnvironmentalInfraction.StatusEnum.RESOLVED);
        infraction3.setFineAmount(25000.0);
        infraction3.setCurrency("PEN");
        infraction3.setReportedAt(java.time.LocalDate.of(2024, 4, 10));
        infractions.add(infraction3);

        return infractions;
    }
}
