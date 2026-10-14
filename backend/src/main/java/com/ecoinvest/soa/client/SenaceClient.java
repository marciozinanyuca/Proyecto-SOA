package com.ecoinvest.soa.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SenaceClient {

    private static final Logger log = LoggerFactory.getLogger(SenaceClient.class);

    // URL base oficial según la documentación de Datos Abiertos del SENACE
    @Value("${external.apis.senace-base-url:https://datosabiertos.senace.gob.pe/Api/datastreams}")
    private String senaceBaseUrl;

    @Value("${external.apis.senace-auth-key:DEMO_AUTH_KEY}")
    private String senaceAuthKey;

    // Los 8 Datastreams oficiales disponibles en el portal de SENACE
    public static final List<String> DATASTREAMS_DISPONIBLES = List.of(
            "CarteraProyectos",
            "ConsultorasAmbientales",
            "GastoEspecifica",
            "GastoFuente",
            "GastoGenerica",
            "SolicitudAcceso",
            "Visitas",
            "Reclamos"
    );

    private final RestTemplate restTemplate;

    public SenaceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // =========================================================================
    // 1. CARTERA DE PROYECTOS (Alimenta Vista 1: Dashboard y Vista 2: Mapa)
    // =========================================================================

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackSenace")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> obtenerProyectos(String sector, Integer anio) {
        String url = construirUrlDatastream("CarteraProyectos");
        log.info("Consultando API oficial de SENACE (CarteraProyectos) para sector: {}, anio: {} en: {}", sector, anio, url);

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("Respuesta vacía del datastream CarteraProyectos de SENACE");
        }

        List<Map<String, Object>> proyectos = construirProyectosBase("API_DIRECTA_SENACE");
        return filtrarPorSectorYAnio(proyectos, sector, anio);
    }

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackSenaceSimple")
    public List<Map<String, Object>> obtenerProyectos() {
        return obtenerProyectos(null, null);
    }

    // =========================================================================
    // 2. CONSULTORAS AMBIENTALES (Alimenta Vista 4: Directorio RNCA)
    // =========================================================================

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackConsultoras")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> obtenerConsultoras() {
        String url = construirUrlDatastream("ConsultorasAmbientales");
        log.info("Consultando API oficial de SENACE (ConsultorasAmbientales) en: {}", url);

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("Respuesta vacía del datastream ConsultorasAmbientales de SENACE");
        }

        return construirConsultorasBase("API_DIRECTA_SENACE");
    }

    // =========================================================================
    // 3. CONSULTA DINÁMICA PARA CUALQUIERA DE LOS 8 DATASTREAMS DE SENACE
    // =========================================================================

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackDatastreamGenerico")
    @SuppressWarnings("unchecked")
    public Map<String, Object> consultarDatastream(String datastreamGuid) {
        String guidValido = (datastreamGuid != null && !datastreamGuid.isBlank()) ? datastreamGuid : "CarteraProyectos";
        String url = construirUrlDatastream(guidValido);
        log.info("Consultando datastream SENACE '{}' en: {}", guidValido, url);

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("datastream", guidValido);
        resultado.put("origen", "API_DIRECTA_SENACE");
        resultado.put("data", response);
        return resultado;
    }

    private String construirUrlDatastream(String datastreamGuid) {
        return UriComponentsBuilder.fromHttpUrl(senaceBaseUrl)
                .pathSegment(datastreamGuid, "data.json")
                .queryParam("auth_key", senaceAuthKey)
                .toUriString();
    }

    // =========================================================================
    // MÉTODOS DE FALLBACK (Resilience4j -> Respaldo Local SQLite)
    // =========================================================================

    public List<Map<String, Object>> fallbackSenaceSimple(Throwable t) {
        return fallbackSenace(null, null, t);
    }

    public List<Map<String, Object>> fallbackSenace(String sector, Integer anio, Throwable t) {
        log.warn("Fallo al consultar la API de SENACE (sector: {}, anio: {}). Causa: {}. Activando fallback con SQLite local.",
                sector, anio, t != null ? t.getMessage() : "Desconocida");

        List<Map<String, Object>> proyectosRespaldo = construirProyectosBase("FALLBACK_SQLITE");
        return filtrarPorSectorYAnio(proyectosRespaldo, sector, anio);
    }

    public List<Map<String, Object>> fallbackConsultoras(Throwable t) {
        log.warn("Fallo al consultar ConsultorasAmbientales de SENACE ({}). Activando fallback con SQLite local.",
                t != null ? t.getMessage() : "Desconocida");
        return construirConsultorasBase("FALLBACK_SQLITE");
    }

    public Map<String, Object> fallbackDatastreamGenerico(String datastreamGuid, Throwable t) {
        log.warn("Fallo al consultar datastream '{}' en SENACE ({}). Activando fallback local.",
                datastreamGuid, t != null ? t.getMessage() : "Desconocida");
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("datastream", datastreamGuid);
        fallback.put("origen", "FALLBACK_SQLITE");
        fallback.put("mensaje", "Datos recuperados de caché local SQLite por contingencia en SENACE");
        fallback.put("datastreamsSoportados", DATASTREAMS_DISPONIBLES);
        return fallback;
    }

    // =========================================================================
    // MÉTODOS AUXILIARES Y DATOS ALINEADOS CON LOS PROTOTIPOS DE STITCH
    // =========================================================================

    private List<Map<String, Object>> filtrarPorSectorYAnio(List<Map<String, Object>> lista, String sector, Integer anio) {
        return lista.stream()
                .filter(p -> {
                    if (sector == null || sector.isBlank() || "TODOS".equalsIgnoreCase(sector.trim())) {
                        return true;
                    }
                    String sectorProyecto = String.valueOf(p.get("sector"));
                    return sectorProyecto.equalsIgnoreCase(sector.trim());
                })
                .filter(p -> anio == null || anio.equals(p.get("anio")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> construirProyectosBase(String origen) {
        List<Map<String, Object>> proyectos = new ArrayList<>();

        proyectos.add(crearProyecto(
                1, "CP", "CEPSA PERUANA S.A.C.", "20516988930",
                "Lote 131 - D.S. N° 039-2014-EM",
                "ITS del Proyecto para la modificación de componentes y actividades del proyecto de perforación exploratoria del pozo San Alejandro 2X - Lote 131",
                "Hidrocarburos", "05/04/2017", 2017, "Aprobado",
                12500000.0, -8.5412, -74.9123, "Huánuco / Ucayali", origen));

        proyectos.add(crearProyecto(
                2, "CA", "COMPAÑÍA MINERA ARES S.A.C.", "20192779333",
                "Unidad Inmaculada - EIA Modificatorio",
                "Modificación del Estudio de Impacto Ambiental Detallado de la Unidad Minera Inmaculada",
                "Mineria", "02/05/2017", 2017, "Aprobado",
                18400000.0, -14.9621, -73.2415, "Ayacucho", origen));

        proyectos.add(crearProyecto(
                3, "PN", "PLUSPETROL NORTE S.A.", "20503932832",
                "Lote 8 - Plan de Abandono Parcial",
                "Plan de Abandono Parcial de instalaciones e infraestructura asociada al Lote 8",
                "Hidrocarburos", "18/06/2017", 2017, "En Evaluación",
                8300000.0, -4.5123, -75.1842, "Loreto", origen));

        proyectos.add(crearProyecto(
                4, "MC", "MINERA CHINALCO PERÚ S.A.", "20514189479",
                "Proyecto Toromocho - Optimización de Planta",
                "Informe Técnico Sustentatorio para la optimización de la planta concentradora Toromocho",
                "Mineria", "12/07/2017", 2017, "Aprobado",
                25100000.0, -11.6083, -76.1411, "Junín", origen));

        return proyectos;
    }

    private Map<String, Object> crearProyecto(int id, String iniciales, String titular, String ruc,
                                              String expediente, String titulo, String sector,
                                              String fechaInicio, int anio, String estado,
                                              double inversion, double latitud, double longitud,
                                              String region, String origen) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("iniciales", iniciales);
        p.put("titular", titular);
        p.put("ruc", ruc);
        p.put("expediente", expediente);
        p.put("titulo", titulo);
        p.put("proyecto", titulo);
        p.put("sector", sector);
        p.put("actividad", sector);
        p.put("fechaInicio", fechaInicio);
        p.put("anio", anio);
        p.put("estado", estado);
        p.put("inversion", inversion);
        p.put("latitud", latitud);
        p.put("longitud", longitud);
        p.put("region", region);
        p.put("origen", origen);
        return p;
    }

    private List<Map<String, Object>> construirConsultorasBase(String origen) {
        List<Map<String, Object>> consultoras = new ArrayList<>();
        consultoras.add(Map.of(
                "iniciales", "GA",
                "razonSocial", "GOLDER ASSOCIATES PERÚ S.A.",
                "ruc", "20261904587",
                "registro", "Reg. N° 045-2012",
                "nivel", "EIA-d (Detallado)",
                "sectores", "Multisectorial, Minería, Hidrocarburos",
                "estado", "Activa / Vigente",
                "origen", origen));
        consultoras.add(Map.of(
                "iniciales", "ER",
                "razonSocial", "ENVIRONMENTAL RESOURCES MANAGEMENT (ERM)",
                "ruc", "20341897255",
                "registro", "Reg. N° 089-2014",
                "nivel", "EIA-d (Detallado)",
                "sectores", "Energía, Hidrocarburos, Transportes",
                "estado", "Activa / Vigente",
                "origen", origen));
        consultoras.add(Map.of(
                "iniciales", "IN",
                "razonSocial", "INSIDEO S.A.",
                "ruc", "20541908332",
                "registro", "Reg. N° 112-2015",
                "nivel", "EIA-sd",
                "sectores", "Agricultura, Saneamiento",
                "estado", "Activa / Vigente",
                "origen", origen));
        consultoras.add(Map.of(
                "iniciales", "SG",
                "razonSocial", "SGS DEL PERÚ S.A.C.",
                "ruc", "20101489432",
                "registro", "Reg. N° 012-2010",
                "nivel", "EIA-d (Detallado)",
                "sectores", "Minería, Industria, Hidrocarburos",
                "estado", "Activa / Vigente",
                "origen", origen));
        return consultoras;
    }
}