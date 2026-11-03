package com.ecoinvest.soa.controller;

import com.ecoinvest.api.ApiApi;
import com.ecoinvest.model.DashboardResponse;
import com.ecoinvest.model.EstadoServiciosResponse;
import com.ecoinvest.model.IndicadorBcrp;
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

        BigDecimal variacion = null;
        if (datosBcrp != null && datosBcrp.get("valor") instanceof BigDecimal valor) {
            variacion = valor;
        }

        // El cliente actual construye registros fijos incluso cuando no hay coincidencias.
        String origenSenace = proyectos.isEmpty() ? "SIMULADO"
                : String.valueOf(proyectos.get(0).getOrDefault("origen", "NO_DISPONIBLE"));
        String origenBcrp = datosBcrp != null
                ? String.valueOf(datosBcrp.getOrDefault("origen", "NO_DISPONIBLE"))
                : "NO_DISPONIBLE";
        if (variacion == null) {
            origenBcrp = "NO_DISPONIBLE";
        }

        IndicadorBcrp indicador = new IndicadorBcrp();
        indicador.setSectorSolicitado(sectorFiltro);
        indicador.setAnioSolicitado(anioFiltro);
        indicador.setOrigen(origenBcrp);
        indicador.setValor(variacion);
        if (datosBcrp != null) {
            indicador.setCodigoSerie((String) datosBcrp.get("codigoSerie"));
            indicador.setDescripcion((String) datosBcrp.get("descripcion"));
            indicador.setUnidad((String) datosBcrp.get("unidad"));
            indicador.setPeriodicidad((String) datosBcrp.get("periodicidad"));
            indicador.setSectorEconomico((String) datosBcrp.get("sectorEconomico"));
            indicador.setAmbito((String) datosBcrp.get("ambito"));
            indicador.setUrlFuente((String) datosBcrp.get("urlFuente"));
            indicador.setPeriodo(variacion != null ? (String) datosBcrp.get("periodo") : null);
            indicador.setMotivo(variacion == null
                    ? (String) datosBcrp.getOrDefault("motivo", "SIN_OBSERVACION") : null);
        } else {
            indicador.setMotivo("ERROR_EXTERNO");
        }

        Map<String, Object> fuentes = new LinkedHashMap<>();
        fuentes.put("senace", origenSenace);
        fuentes.put("bcrp", origenBcrp);
        fuentes.put("sectorConsultado", sectorFiltro);
        fuentes.put("anioConsultado", anioFiltro);

        DashboardResponse response = new DashboardResponse();
        response.setTotal(total);
        response.setAprobados(aprobados);
        response.setVariacionSeleccionada(variacion);
        response.setIndicadorBcrp(indicador);
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
    @Override
    public ResponseEntity<List<Object>> obtenerConsultoras() {
        return ResponseEntity.ok(new ArrayList<>(senaceClient.obtenerConsultoras()));
    }

    // 4. Endpoint adicional para consultar cualquiera de los 8 Datastreams de SENACE
    @Override
    public ResponseEntity<Map<String, Object>> consultarDatastream(String nombre) {
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
        info.put("respaldoLocal", "NO_IMPLEMENTADO");
        return info;
    }
}
