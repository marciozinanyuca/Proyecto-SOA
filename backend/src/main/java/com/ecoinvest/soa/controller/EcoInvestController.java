package com.ecoinvest.soa.controller;

import com.ecoinvest.api.ApiApi;
import com.ecoinvest.model.AdvertenciaSenace;
import com.ecoinvest.model.CoberturaSenace;
import com.ecoinvest.model.ConsultaSenace;
import com.ecoinvest.model.ConsultoraSenacePublica;
import com.ecoinvest.model.ConteosSenace;
import com.ecoinvest.model.DashboardResponse;
import com.ecoinvest.model.DatastreamSenaceResponse;
import com.ecoinvest.model.EstadoConsultaSenace;
import com.ecoinvest.model.EstadoFiltradoSenace;
import com.ecoinvest.model.EstadoServiciosResponse;
import com.ecoinvest.model.FuentesDashboard;
import com.ecoinvest.model.IndicadorBcrp;
import com.ecoinvest.model.MotivoSenace;
import com.ecoinvest.model.OrigenSenace;
import com.ecoinvest.model.ProyectoSenacePublico;
import com.ecoinvest.soa.client.BcrpClient;
import com.ecoinvest.soa.client.ResultadoConsultaSenace;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Advertencia;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Conteos;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoConsulta;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoFiltrado;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Motivo;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Origen;
import com.ecoinvest.soa.client.SenaceClient;
import com.ecoinvest.soa.client.SenaceClient.ConsultaSenaceException;
import com.ecoinvest.soa.client.SenaceMapper.ConsultoraMapeada;
import com.ecoinvest.soa.client.SenaceMapper.ProyectoMapeado;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class EcoInvestController implements ApiApi {
    private static final String ORIGEN = "X-Senace-Origen";
    private static final String ESTADO = "X-Senace-Estado";
    private static final String COBERTURA = "X-Senace-Cobertura";
    private static final String MOTIVO = "X-Senace-Motivo";
    private static final String COMPLETO = "X-Senace-Cuerpo-Completo";

    private final SenaceClient senaceClient;
    private final BcrpClient bcrpClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public EcoInvestController(SenaceClient senaceClient, BcrpClient bcrpClient,
                               CircuitBreakerRegistry circuitBreakerRegistry) {
        this.senaceClient = senaceClient;
        this.bcrpClient = bcrpClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public ResponseEntity<DashboardResponse> obtenerDashboard(String sector, Integer anio) {
        String sectorFiltro = sector != null && !sector.isBlank() ? sector : "Hidrocarburos";
        Integer anioFiltro = anio != null ? anio : 2017;
        ResultadoConsultaSenace<ProyectoMapeado> senace = consultarSeguro(
                () -> senaceClient.consultarProyectos(sectorFiltro, anioFiltro));

        // Proveedores independientes: el resultado SENACE nunca impide esta llamada.
        Map<String, Object> datosBcrp;
        try {
            datosBcrp = bcrpClient.obtenerDatosBcrp(sectorFiltro, anioFiltro);
        } catch (RuntimeException exception) {
            // No conservar mensaje, causa o cuerpo externo inesperados.
            datosBcrp = null;
        }
        IndicadorBcrp indicador = indicadorBcrp(datosBcrp, sectorFiltro, anioFiltro);
        FuentesDashboard fuentes = new FuentesDashboard();
        fuentes.setSenace(OrigenSenace.fromValue(senace.origen().name()));
        fuentes.setBcrp(indicador.getOrigen());
        fuentes.setSectorConsultado(sectorFiltro);
        fuentes.setAnioConsultado(anioFiltro);

        DashboardResponse response = new DashboardResponse();
        response.setIndicadorBcrp(indicador);
        response.setVariacionSeleccionada(indicador.getValor());
        response.setFuentes(fuentes);
        response.setConsultaSenace(consultaPublica(senace));
        if (disponible(senace)) {
            // Filas ya filtradas por el mapper: no repetir reglas temporales o sectoriales.
            response.setTotal(senace.registros().size());
            response.setAprobados((int) senace.registros().stream()
                    .filter(p -> "Aprobado".equalsIgnoreCase(p.estado())).count());
            response.setProyectos(senace.registros().stream().map(this::proyectoPublico).toList());
        } else {
            // Evitar tambien los defaults de colecciones que pueda generar OpenAPI.
            response.setTotal(null);
            response.setAprobados(null);
            response.setProyectos(null);
        }
        return ResponseEntity.ok(response);
    }

    @Override
    @CrossOrigin(exposedHeaders = {ORIGEN, ESTADO, COBERTURA, MOTIVO, COMPLETO})
    public ResponseEntity<List<ConsultoraSenacePublica>> obtenerConsultoras() {
        ResultadoConsultaSenace<ConsultoraMapeada> resultado = consultarSeguro(senaceClient::consultarConsultoras);
        ConsultaSenace consulta = consultaPublica(resultado);
        List<ConsultoraSenacePublica> filas = disponible(resultado)
                ? resultado.registros().stream().map(this::consultoraPublica).toList() : List.of();
        return ResponseEntity.status(estadoHttp(consulta)).headers(headersSenace(consulta)).body(filas);
    }

    @Override
    public ResponseEntity<DatastreamSenaceResponse> consultarDatastream(String nombre) {
        // El generico conserva objeto y cuerpo opaco solo en respuesta admitida.
        String canonico = nombreSeguro(nombre);
        try {
            Map<String, Object> raw = senaceClient.consultarDatastream(nombre);
            if (raw == null || texto(raw, "datastream") == null
                    || !canonico.equals(nombreSeguro(texto(raw, "datastream")))) {
                return genericoError(canonico, error(EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO, null));
            }
            ConsultaSenace consulta = consultaGenerica(raw.get("consulta"));
            if (!consulta.getOrigen().toString().equals(texto(raw, "origen"))) {
                throw new IllegalArgumentException("METADATOS_INCOHERENTES");
            }
            boolean validado = "CarteraProyectos".equals(canonico) || "ConsultorasAmbientales".equals(canonico);
            boolean disponible = estadoHttp(consulta) == HttpStatus.OK;
            Object data = disponible ? raw.get("data") : null;
            if (disponible && ("NO_RECONOCIDO".equals(canonico)
                    || (validado ? !(data instanceof List<?>) : !(data instanceof Map<?, ?>)))) {
                throw new IllegalArgumentException("RAIZ_INCOHERENTE");
            }
            if (disponible && consulta.getEstado() == EstadoConsultaSenace.VACIA
                    && (!(data instanceof List<?>) || !((List<?>) data).isEmpty())) {
                throw new IllegalArgumentException("VACIO_INCOHERENTE");
            }
            DatastreamSenaceResponse response = envolvente(canonico, consulta, data, validado);
            return ResponseEntity.status(estadoHttp(consulta)).body(response);
        } catch (ConsultaSenaceException exception) {
            return genericoError(canonico, exception.resultado());
        } catch (IllegalArgumentException exception) {
            return genericoError(canonico, error(EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO, null));
        } catch (RuntimeException exception) {
            return genericoError(canonico, ResultadoConsultaSenace.errorExterno());
        }
    }

    @Override
    public ResponseEntity<EstadoServiciosResponse> obtenerEstadoServicios() {
        EstadoServiciosResponse response = new EstadoServiciosResponse();
        response.setSenace(construirMetricasCircuitBreaker("senace"));
        response.setBcrp(construirMetricasCircuitBreaker("bcrp"));
        return ResponseEntity.ok(response);
    }

    private static <T> ResultadoConsultaSenace<T> consultarSeguro(Supplier<ResultadoConsultaSenace<T>> consulta) {
        try {
            ResultadoConsultaSenace<T> result = consulta.get();
            if (result == null) return ResultadoConsultaSenace.errorExterno();
            if ((result.estadoConsulta() == EstadoConsulta.EXITO || result.estadoConsulta() == EstadoConsulta.VACIA)
                    && (result.origen() != Origen.API_DIRECTA_SENACE || !Boolean.TRUE.equals(result.cuerpoCompleto()))) {
                return error(EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO, result.cuerpoCompleto());
            }
            return result;
        } catch (ConsultaSenaceException exception) {
            ResultadoConsultaSenace<?> result = exception.resultado();
            return new ResultadoConsultaSenace<>(List.of(), Origen.NO_DISPONIBLE, result.estadoConsulta(),
                    result.estadoFiltrado(), result.cobertura(), result.cuerpoCompleto(), result.motivo(),
                    result.conteos(), result.advertencias());
        } catch (RuntimeException exception) {
            return ResultadoConsultaSenace.errorExterno();
        }
    }

    private static <T> ResultadoConsultaSenace<T> error(EstadoConsulta estado, Motivo motivo, Boolean completo) {
        return new ResultadoConsultaSenace<>(List.of(), Origen.NO_DISPONIBLE, estado, EstadoFiltrado.NO_APLICABLE,
                ResultadoConsultaSenace.Cobertura.NO_VERIFICADA, completo, motivo, Conteos.desconocidos(), List.of());
    }

    private static boolean disponible(ResultadoConsultaSenace<?> resultado) {
        return resultado.origen() == Origen.API_DIRECTA_SENACE
                && (resultado.estadoConsulta() == EstadoConsulta.EXITO || resultado.estadoConsulta() == EstadoConsulta.VACIA)
                && Boolean.TRUE.equals(resultado.cuerpoCompleto());
    }

    private static HttpStatus estadoHttp(ConsultaSenace consulta) {
        if (consulta.getEstado() == EstadoConsultaSenace.FORMATO_INVALIDO) return HttpStatus.BAD_GATEWAY;
        return (consulta.getEstado() == EstadoConsultaSenace.EXITO || consulta.getEstado() == EstadoConsultaSenace.VACIA)
                && consulta.getOrigen() == OrigenSenace.API_DIRECTA_SENACE
                && Boolean.TRUE.equals(consulta.getCuerpoCompleto())
                ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    }

    private ConsultaSenace consultaPublica(ResultadoConsultaSenace<?> result) {
        return consultaPublica(result.estadoConsulta(), result.origen(), result.estadoFiltrado(),
                result.cuerpoCompleto(), result.motivo(), result.conteos(), result.advertencias());
    }

    private ConsultaSenace consultaPublica(EstadoConsulta estado, Origen origen, EstadoFiltrado filtrado,
            Boolean completo, Motivo motivo, Conteos conteos, List<Advertencia> advertencias) {
        ConsultaSenace response = new ConsultaSenace();
        response.setEstado(EstadoConsultaSenace.fromValue(estado.name()));
        response.setOrigen(OrigenSenace.fromValue(origen.name()));
        response.setEstadoFiltrado(EstadoFiltradoSenace.fromValue(filtrado.name()));
        response.setCobertura(CoberturaSenace.NO_VERIFICADA);
        response.setCuerpoCompleto(completo);
        response.setMotivo(motivo == null ? null : MotivoSenace.fromValue(motivo.name()));
        ConteosSenace counts = new ConteosSenace();
        counts.setRecibidos(conteos.recibidos());
        counts.setAceptados(conteos.aceptados());
        counts.setDevueltos(conteos.devueltos());
        counts.setAprobados(conteos.aprobados());
        counts.setFechasNoEvaluables(conteos.fechasNoEvaluables());
        counts.setFechasNoEvaluablesEnActividad(conteos.fechasNoEvaluablesEnActividad());
        response.setConteos(counts);
        response.setAdvertencias(advertencias.stream().map(a -> AdvertenciaSenace.fromValue(a.name())).toList());
        return response;
    }

    private ConsultaSenace consultaGenerica(Object raw) {
        if (!(raw instanceof Map<?, ?> meta)) throw new IllegalArgumentException("METADATOS_AUSENTES");
        EstadoConsulta estado = EstadoConsulta.valueOf(valorEnum(meta.get("estado")));
        Origen origen = Origen.valueOf(valorEnum(meta.get("origen")));
        EstadoFiltrado filtrado = EstadoFiltrado.valueOf(valorEnum(meta.get("estadoFiltrado")));
        if (!"NO_VERIFICADA".equals(meta.get("cobertura"))) throw new IllegalArgumentException("COBERTURA_INVALIDA");
        Object completo = meta.get("cuerpoCompleto");
        if (completo != null && !(completo instanceof Boolean)) throw new IllegalArgumentException("COMPLETITUD_INVALIDA");
        Motivo motivo = meta.get("motivo") == null ? null : Motivo.valueOf(valorEnum(meta.get("motivo")));
        Conteos conteos = meta.get("conteos") == null ? Conteos.desconocidos()
                : meta.get("conteos") instanceof Conteos c ? c : conteosGenericos(meta.get("conteos"));
        List<Advertencia> advertencias = new ArrayList<>();
        if (meta.get("advertencias") instanceof List<?> lista) {
            for (Object item : lista) advertencias.add(item instanceof Advertencia a ? a : Advertencia.valueOf(valorEnum(item)));
        } else if (meta.get("advertencias") != null) {
            throw new IllegalArgumentException("ADVERTENCIAS_INVALIDAS");
        }
        if (origen == Origen.SIMULADO || ((estado == EstadoConsulta.EXITO || estado == EstadoConsulta.VACIA)
                && (origen != Origen.API_DIRECTA_SENACE || !Boolean.TRUE.equals(completo)))) {
            throw new IllegalArgumentException("PROCEDENCIA_INCOHERENTE");
        }
        if (estado != EstadoConsulta.EXITO && estado != EstadoConsulta.VACIA && origen != Origen.NO_DISPONIBLE) {
            throw new IllegalArgumentException("ERROR_CON_ORIGEN_INCOHERENTE");
        }
        return consultaPublica(estado, origen, filtrado, (Boolean) completo, motivo, conteos, advertencias);
    }

    private static Conteos conteosGenericos(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("CONTEOS_INVALIDOS");
        return new Conteos(entero(map.get("recibidos")), entero(map.get("aceptados")), entero(map.get("devueltos")),
                entero(map.get("aprobados")), entero(map.get("fechasNoEvaluables")), entero(map.get("fechasNoEvaluablesEnActividad")));
    }

    private static Integer entero(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        throw new IllegalArgumentException("CONTEO_INVALIDO");
    }

    private static String valorEnum(Object value) {
        if (value instanceof String s) return s;
        throw new IllegalArgumentException("ENUM_INVALIDO");
    }

    private ResponseEntity<DatastreamSenaceResponse> genericoError(String nombre, ResultadoConsultaSenace<?> error) {
        ConsultaSenace consulta = consultaPublica(error);
        boolean validado = "CarteraProyectos".equals(nombre) || "ConsultorasAmbientales".equals(nombre);
        return ResponseEntity.status(estadoHttp(consulta)).body(envolvente(nombre, consulta, null, validado));
    }

    private DatastreamSenaceResponse envolvente(String nombre, ConsultaSenace consulta, Object data, boolean validado) {
        DatastreamSenaceResponse response = new DatastreamSenaceResponse();
        response.setDatastream(nombre);
        response.setOrigen(consulta.getOrigen());
        response.data(data);
        response.setConsulta(consulta);
        response.setRecursoValidado(validado);
        if (estadoHttp(consulta) != HttpStatus.OK) {
            response.data(null);
            response.setMensaje("Datos SENACE no disponibles; consultar motivo seguro");
            response.setDatastreamsSoportados(SenaceClient.DATASTREAMS_DISPONIBLES);
        } else {
            response.setDatastreamsSoportados(null);
        }
        return response;
    }

    private static String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) return "CarteraProyectos";
        for (String permitido : SenaceClient.DATASTREAMS_DISPONIBLES) {
            if (permitido.equalsIgnoreCase(nombre.trim())) return permitido;
        }
        return "NO_RECONOCIDO";
    }

    private static String texto(Map<String, Object> map, String name) {
        return map.get(name) instanceof String text ? text : null;
    }

    private static HttpHeaders headersSenace(ConsultaSenace consulta) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ORIGEN, consulta.getOrigen().toString());
        headers.set(ESTADO, consulta.getEstado().toString());
        headers.set(COBERTURA, consulta.getCobertura().toString());
        if (consulta.getMotivo() != null) headers.set(MOTIVO, consulta.getMotivo().toString());
        if (consulta.getCuerpoCompleto() != null) headers.set(COMPLETO, consulta.getCuerpoCompleto().toString());
        return headers;
    }

    private ProyectoSenacePublico proyectoPublico(ProyectoMapeado row) {
        ProyectoSenacePublico out = new ProyectoSenacePublico();
        out.setId(row.id());
        out.setTitular(row.titular());
        out.setRuc(row.ruc());
        out.setTitulo(row.tituloProyecto());
        out.setProyecto(row.tituloProyecto());
        out.setUnidadProyecto(row.unidadProyecto());
        out.setTipo(row.tipo());
        out.setActividad(row.actividad());
        out.setSector(row.actividad());
        out.setFechaInicio(row.fechaInicio());
        out.setAnio(row.anioInicio());
        out.setEstado(row.estado());
        out.setDescripcion(row.descripcion());
        out.setLongitud(row.longitud());
        out.setLatitud(row.latitud());
        out.setResolucion(row.resolucion());
        out.setOrigen(OrigenSenace.API_DIRECTA_SENACE);
        return out;
    }

    private ConsultoraSenacePublica consultoraPublica(ConsultoraMapeada row) {
        ConsultoraSenacePublica out = new ConsultoraSenacePublica();
        out.setRazonSocial(row.razonSocial());
        out.setActividad(row.actividad());
        out.setSubsector(row.subsector());
        out.setTipoSolicitud(row.tipoSolicitud());
        out.setResolucion(row.resolucion());
        out.setFechaRd(row.fechaRd());
        out.setInicioVigenciaRd(row.inicioVigenciaRd());
        out.setFinVigenciaRd(row.finVigenciaRd());
        return out;
    }

    private static IndicadorBcrp indicadorBcrp(Map<String, Object> datos, String sector, Integer anio) {
        BigDecimal valor = datos != null && datos.get("valor") instanceof BigDecimal decimal ? decimal : null;
        String origen = datos != null ? texto(datos, "origen") : "NO_DISPONIBLE";
        if (valor == null || origen == null) origen = "NO_DISPONIBLE";
        IndicadorBcrp indicador = new IndicadorBcrp();
        indicador.setSectorSolicitado(sector);
        indicador.setAnioSolicitado(anio);
        indicador.setOrigen(origen);
        indicador.setValor(valor);
        if (datos == null) {
            indicador.setMotivo("ERROR_EXTERNO");
        } else {
            indicador.setCodigoSerie(texto(datos, "codigoSerie"));
            indicador.setDescripcion(texto(datos, "descripcion"));
            indicador.setUnidad(texto(datos, "unidad"));
            indicador.setPeriodicidad(texto(datos, "periodicidad"));
            indicador.setSectorEconomico(texto(datos, "sectorEconomico"));
            indicador.setAmbito(texto(datos, "ambito"));
            indicador.setUrlFuente(texto(datos, "urlFuente"));
            indicador.setPeriodo(valor != null ? texto(datos, "periodo") : null);
            indicador.setMotivo(valor == null ? (datos.containsKey("motivo") ? texto(datos, "motivo") : "SIN_OBSERVACION") : null);
        }
        return indicador;
    }

    private Map<String, Object> construirMetricasCircuitBreaker(String nombreInstancia) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(nombreInstancia);
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("servicio", nombreInstancia.toUpperCase(Locale.ROOT));
        info.put("estado", cb.getState().name());
        info.put("tasaFallosPorcentaje", metrics.getFailureRate());
        info.put("llamadasFallidas", metrics.getNumberOfFailedCalls());
        info.put("llamadasExitosas", metrics.getNumberOfSuccessfulCalls());
        info.put("respaldoLocal", "NO_IMPLEMENTADO");
        info.put("alcanceMetricas", "LLAMADAS_LOGICAS");
        info.put("disponibilidadExterna", "NO_VERIFICADA");
        return info;
    }
}
