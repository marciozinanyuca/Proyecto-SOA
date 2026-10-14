package com.ecoinvest.soa.controller;

import com.ecoinvest.api.ApiApi;
import com.ecoinvest.model.DashboardResponse;
import com.ecoinvest.model.EstadoServiciosResponse;
import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.SenaceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class EcoInvestController implements ApiApi {

    private final SenaceClient senaceClient;
    private final BcrpClient bcrpClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public EcoInvestController(SenaceClient senaceClient,
                               BcrpClient bcrpClient,
                               CircuitBreakerRegistry circuitBreakerRegistry) {
        this.senaceClient = senaceClient;
        this.bcrpClient = bcrpClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    // 1. Endpoint principal del contrato OpenAPI: /api/dashboard
    @Override
    public ResponseEntity<DashboardResponse> obtenerDashboard(String sector, Integer anio) {
        String sectorFiltro = (sector != null && !sector.isBlank()) ? sector : "Hidrocarburos";
        Integer anioFiltro = (anio != null) ? anio : 2017;

        List<Map<String, Object>> proyectos = senaceClient.obtenerProyectos(sectorFiltro, anioFiltro);
        Map<String, Object> datosBcrp = bcrpClient.obtenerDatosBcrp(sectorFiltro, anioFiltro);

        int total = proyectos.size();
        int aprobados = (int) proyectos.stream()
                .filter(p -> "Aprobado".equalsIgnoreCase(String.valueOf(p.get("estado"))))
                .count();

        double variacion = -2.45;
        if (datosBcrp != null && datosBcrp.get("variacionSeleccionada") instanceof Number num) {
            variacion = num.doubleValue();
        }

        String origenSenace = proyectos.isEmpty() ? "FALLBACK_SQLITE"
                : String.valueOf(proyectos.get(0).getOrDefault("origen", "FALLBACK_SQLITE"));
        String origenBcrp = datosBcrp != null
                ? String.valueOf(datosBcrp.getOrDefault("origen", "FALLBACK_SQLITE"))
                : "FALLBACK_SQLITE";

        Map<String, Object> fuentes = new LinkedHashMap<>();
        fuentes.put("senace", origenSenace);
        fuentes.put("bcrp", origenBcrp);
        fuentes.put("sectorConsultado", sectorFiltro);
        fuentes.put("anioConsultado", anioFiltro);

        DashboardResponse response = new DashboardResponse();
        response.setTotal(total);
        response.setAprobados(aprobados);
        response.setVariacionSeleccionada(BigDecimal.valueOf(variacion));
        response.setProyectos(new ArrayList<>(proyectos));
        response.setFuentes(fuentes);

        return ResponseEntity.ok(response);
    }

    // 2. Endpoint del contrato OpenAPI para el Monitor de Salud SOA: /api/estado-servicios
    @Override
    public ResponseEntity<EstadoServiciosResponse> obtenerEstadoServicios() {
        EstadoServiciosResponse response = new EstadoServiciosResponse();
        response.setSenace(construirMetricasCircuitBreaker("senace"));
        response.setBcrp(construirMetricasCircuitBreaker("bcrp"));
        return ResponseEntity.ok(response);
    }

    // 3. Endpoint adicional para la Vista 4 (Directorio de Consultoras RNCA)
    @GetMapping("/api/consultoras")
    public ResponseEntity<List<Map<String, Object>>> obtenerConsultoras() {
        return ResponseEntity.ok(senaceClient.obtenerConsultoras());
    }

    // 4. Endpoint adicional para consultar cualquiera de los 8 Datastreams de SENACE
    @GetMapping("/api/senace/datastream")
    public ResponseEntity<Map<String, Object>> consultarDatastream(
            @RequestParam(defaultValue = "CarteraProyectos") String nombre) {
        return ResponseEntity.ok(senaceClient.consultarDatastream(nombre));
    }

    private Map<String, Object> construirMetricasCircuitBreaker(String nombreInstancia) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(nombreInstancia);
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("servicio", nombreInstancia.toUpperCase());
        info.put("estado", cb.getState().name());
        info.put("tasaFallosPorcentaje", metrics.getFailureRate());
        info.put("llamadasFallidas", metrics.getNumberOfFailedCalls());
        info.put("llamadasExitosas", metrics.getNumberOfSuccessfulCalls());
        info.put("respaldoLocal", "SQLite Activo (ecoinvest_fallback.db)");
        return info;
    }
}