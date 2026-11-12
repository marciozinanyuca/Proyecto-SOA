package com.ecoinvest.soa.client;

import com.ecoinvest.soa.client.ResultadoConsultaSenace.Cobertura;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Conteos;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoConsulta;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoFiltrado;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Motivo;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Origen;
import com.ecoinvest.soa.client.SenaceMapper.ConsultoraMapeada;
import com.ecoinvest.soa.client.SenaceMapper.ProyectoMapeado;
import com.ecoinvest.soa.client.SenaceTransport.Recurso;
import com.ecoinvest.soa.client.SenaceTransport.RespuestaTransporte;
import com.ecoinvest.soa.client.dto.ConsultoraSenaceDto;
import com.ecoinvest.soa.client.dto.ProyectoSenaceDto;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Cliente SENACE: transporte unico, DTOs externos y mapper puro; sin datos de sustitucion. */
@Component
public class SenaceClient {
    private static final Logger log = LoggerFactory.getLogger(SenaceClient.class);
    public static final List<String> DATASTREAMS_DISPONIBLES = List.of(
            "CarteraProyectos", "ConsultorasAmbientales", "GastoEspecifica", "GastoFuente",
            "GastoGenerica", "SolicitudAcceso", "Visitas", "Reclamos");
    private final SenaceTransport transport;
    private final ObjectMapper jackson;
    private final SenaceMapper mapper = new SenaceMapper();

    public SenaceClient(SenaceTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport);
        this.jackson = Objects.requireNonNull(objectMapper).copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                        DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                        DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS,
                        DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        // Copia privada: no alterar Jackson compartido ni coercer RUC/textos desde numeros.
        for (CoercionInputShape shape : List.of(CoercionInputShape.Integer,
                CoercionInputShape.Float, CoercionInputShape.Boolean)) {
            jackson.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
        }
        jackson.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
    }

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackConsultaProyectos")
    public ResultadoConsultaSenace<ProyectoMapeado> consultarProyectos(String sector, Integer anio) {
        return ejecutarProyectos(sector, anio);
    }

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackConsultaConsultoras")
    public ResultadoConsultaSenace<ConsultoraMapeada> consultarConsultoras() {
        return ejecutarConsultoras();
    }

    /** Entrada interna LKG, sin filtro anual. El coordinador debe invocar el proxy Spring. */
    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackCarteraCompleta")
    public ResultadoConsultaSenace<ProyectoMapeado> consultarCarteraCompleta() {
        RespuestaTransporte respuesta = recibir(Recurso.CARTERA_PROYECTOS);
        return mapearCarteraCompleta(deserializarLista(respuesta, ProyectoSenaceDto.class), respuesta);
    }

    /** Adaptador transitorio: errores no se convierten en conteos oficiales cero. */
    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackSenace")
    public List<Map<String, Object>> obtenerProyectos(String sector, Integer anio) {
        return proyectosCompatibles(ejecutarProyectos(sector, anio));
    }

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackSenaceSimple")
    public List<Map<String, Object>> obtenerProyectos() {
        // No depender de autoinvocacion entre metodos anotados.
        return proyectosCompatibles(ejecutarProyectos(null, null));
    }

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackConsultoras")
    public List<Map<String, Object>> obtenerConsultoras() {
        ResultadoConsultaSenace<ConsultoraMapeada> resultado = ejecutarConsultoras();
        List<Map<String, Object>> filas = new ArrayList<>();
        for (ConsultoraMapeada row : resultado.registros()) {
            Map<String, Object> map = new LinkedHashMap<>();
            poner(map, "razonSocial", row.razonSocial());
            poner(map, "actividad", row.actividad());
            poner(map, "subsector", row.subsector());
            poner(map, "tipoSolicitud", row.tipoSolicitud());
            poner(map, "resolucion", row.resolucion());
            poner(map, "fechaRd", row.fechaRd());
            poner(map, "inicioVigenciaRd", row.inicioVigenciaRd());
            poner(map, "finVigenciaRd", row.finVigenciaRd());
            map.put("origen", resultado.origen().name());
            filas.add(map);
        }
        return filas;
    }

    @CircuitBreaker(name = "senace", fallbackMethod = "fallbackDatastreamGenerico")
    public Map<String, Object> consultarDatastream(String nombre) {
        Recurso recurso = resolverRecurso(nombre);
        if (recurso == null) throw fallo(EstadoConsulta.ERROR_EXTERNO, Motivo.RECURSO_INVALIDO, null, null);
        RespuestaTransporte respuesta = recibir(recurso);
        Object data;
        ResultadoConsultaSenace<?> consulta;
        if (recurso == Recurso.CARTERA_PROYECTOS) {
            List<ProyectoSenaceDto> rows = deserializarLista(respuesta, ProyectoSenaceDto.class);
            consulta = mapearCarteraCompleta(rows, respuesta);
            data = rows;
        } else if (recurso == Recurso.CONSULTORAS_AMBIENTALES) {
            List<ConsultoraSenaceDto> rows = deserializarLista(respuesta, ConsultoraSenaceDto.class);
            consulta = comprobarMapa(mapper.mapearConsultoras(rows), respuesta);
            data = rows;
        } else {
            // Compatibilidad heredada: objeto opaco, sin semantica sectorial o de filas inventada.
            data = deserializarObjeto(respuesta);
            consulta = new ResultadoConsultaSenace<>(List.of(), Origen.API_DIRECTA_SENACE,
                    EstadoConsulta.EXITO, EstadoFiltrado.NO_APLICABLE, Cobertura.NO_VERIFICADA,
                    respuesta.cuerpoCompleto(), null, Conteos.desconocidos(), List.of());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datastream", recurso.nombre());
        result.put("origen", consulta.origen().name());
        result.put("data", data);
        result.put("consulta", metadatos(consulta));
        result.put("recursoValidado", recurso.verificado());
        return result;
    }

    private ResultadoConsultaSenace<ProyectoMapeado> mapearCarteraCompleta(
            List<ProyectoSenaceDto> rows, RespuestaTransporte respuesta) {
        List<ProyectoMapeado> mapeados = new ArrayList<>();
        try {
            for (ProyectoSenaceDto row : rows) mapeados.add(mapper.mapearProyecto(row));
        } catch (IllegalArgumentException exception) {
            Motivo motivo = "FILA_NULA".equals(exception.getMessage()) ? Motivo.FILA_NULA
                    : "ID_AUSENTE".equals(exception.getMessage()) ? Motivo.ID_AUSENTE : Motivo.FORMATO_INVALIDO;
            throw fallo(EstadoConsulta.FORMATO_INVALIDO, motivo, respuesta.cuerpoCompleto(), rows.size());
        }
        return new ResultadoConsultaSenace<>(mapeados, Origen.API_DIRECTA_SENACE,
                rows.isEmpty() ? EstadoConsulta.VACIA : EstadoConsulta.EXITO, EstadoFiltrado.NO_APLICABLE,
                Cobertura.NO_VERIFICADA, respuesta.cuerpoCompleto(),
                rows.isEmpty() ? Motivo.RESPUESTA_OFICIAL_VACIA : null,
                new Conteos(rows.size(), rows.size(), rows.size(), null,
                        (int) mapeados.stream().filter(p -> p.anioInicio() == null).count(), null),
                mapeados.stream().flatMap(p -> p.advertencias().stream()).distinct().toList());
    }

    private ResultadoConsultaSenace<ProyectoMapeado> ejecutarProyectos(String sector, Integer anio) {
        RespuestaTransporte respuesta = recibir(Recurso.CARTERA_PROYECTOS);
        List<ProyectoSenaceDto> rows = deserializarLista(respuesta, ProyectoSenaceDto.class);
        return comprobarMapa(mapper.mapearProyectos(rows, sector, anio), respuesta);
    }

    private ResultadoConsultaSenace<ConsultoraMapeada> ejecutarConsultoras() {
        RespuestaTransporte respuesta = recibir(Recurso.CONSULTORAS_AMBIENTALES);
        List<ConsultoraSenaceDto> rows = deserializarLista(respuesta, ConsultoraSenaceDto.class);
        return comprobarMapa(mapper.mapearConsultoras(rows), respuesta);
    }

    private RespuestaTransporte recibir(Recurso recurso) {
        final RespuestaTransporte respuesta;
        try {
            respuesta = transport.consultar(recurso);
        } catch (RuntimeException exception) {
            // Una causa inesperada de infraestructura puede incluir URI o cuerpo autenticado.
            throw fallo(EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_EXTERNO, null, null);
        }
        if (respuesta == null) throw fallo(EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_EXTERNO, null, null);
        Boolean completo = respuesta.codigoHttp() == null && respuesta.bytesLeidos() == 0
                ? null : respuesta.cuerpoCompleto();
        if (respuesta.estado() == SenaceTransport.Estado.RECIBIDO) {
            if (!respuesta.cuerpoCompleto()) throw fallo(EstadoConsulta.PARCIAL, Motivo.RESPUESTA_PARCIAL, false, null);
            if (!Integer.valueOf(200).equals(respuesta.codigoHttp())) {
                throw fallo(EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_HTTP, completo, null);
            }
            if (respuesta.cuerpo() == null) throw fallo(EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO, completo, null);
            return respuesta;
        }
        Motivo motivo = switch (respuesta.estado()) {
            case CONFIGURACION_AUSENTE -> Motivo.CONFIGURACION_AUSENTE;
            case CONFIGURACION_INVALIDA -> Motivo.CONFIGURACION_INVALIDA;
            case DESTINO_INVALIDO -> Motivo.DESTINO_INVALIDO;
            case RECURSO_INVALIDO -> Motivo.RECURSO_INVALIDO;
            case REDIRECCION_RECHAZADA -> Motivo.REDIRECCION_RECHAZADA;
            case ERROR_HTTP -> Motivo.ERROR_HTTP;
            case ERROR_TLS -> Motivo.ERROR_TLS;
            case TIMEOUT_CONEXION -> Motivo.TIMEOUT_CONEXION;
            case TIMEOUT_INACTIVIDAD -> Motivo.TIMEOUT_INACTIVIDAD;
            case PLAZO_TOTAL_AGOTADO -> Motivo.PLAZO_TOTAL_AGOTADO;
            case LIMITE_TAMANO -> Motivo.LIMITE_LECTURA;
            case RESPUESTA_INCOMPLETA -> Motivo.RESPUESTA_PARCIAL;
            case CODIFICACION_NO_ADMITIDA -> Motivo.CODIFICACION_NO_ADMITIDA;
            case HTML_INESPERADO, JSON_INVALIDO, ESTRUCTURA_INVALIDA -> Motivo.FORMATO_INVALIDO;
            case INTERRUMPIDO -> Motivo.INTERRUMPIDO;
            default -> Motivo.ERROR_EXTERNO;
        };
        EstadoConsulta estado = switch (respuesta.estado()) {
            case HTML_INESPERADO, JSON_INVALIDO, ESTRUCTURA_INVALIDA, CODIFICACION_NO_ADMITIDA -> EstadoConsulta.FORMATO_INVALIDO;
            case LIMITE_TAMANO, RESPUESTA_INCOMPLETA -> EstadoConsulta.PARCIAL;
            default -> respuesta.bytesLeidos() > 0 && !respuesta.cuerpoCompleto()
                    ? EstadoConsulta.PARCIAL : EstadoConsulta.ERROR_EXTERNO;
        };
        throw fallo(estado, motivo, completo, null);
    }

    private <T> List<T> deserializarLista(RespuestaTransporte respuesta, Class<T> tipo) {
        JavaType lista = jackson.getTypeFactory().constructCollectionType(List.class, tipo);
        return deserializar(respuesta, lista, JsonToken.START_ARRAY);
    }

    private Map<String, Object> deserializarObjeto(RespuestaTransporte respuesta) {
        JavaType objeto = jackson.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
        return deserializar(respuesta, objeto, JsonToken.START_OBJECT);
    }

    private <T> T deserializar(RespuestaTransporte respuesta, JavaType tipo, JsonToken raiz) {
        try (JsonParser parser = jackson.createParser(respuesta.cuerpo())) {
            if (parser.nextToken() != raiz) throw fallo(EstadoConsulta.FORMATO_INVALIDO,
                    Motivo.FORMATO_INVALIDO, respuesta.cuerpoCompleto(), null);
            return jackson.readerFor(tipo).readValue(parser);
        } catch (IOException | IllegalArgumentException exception) {
            throw fallo(EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO, respuesta.cuerpoCompleto(), null);
        }
    }

    private static <T> ResultadoConsultaSenace<T> comprobarMapa(
            ResultadoConsultaSenace<T> resultado, RespuestaTransporte respuesta) {
        ResultadoConsultaSenace<T> conEvidencia = new ResultadoConsultaSenace<>(resultado.registros(),
                resultado.origen(), resultado.estadoConsulta(), resultado.estadoFiltrado(), resultado.cobertura(),
                respuesta.cuerpoCompleto(), resultado.motivo(), resultado.conteos(), resultado.advertencias());
        if (conEvidencia.estadoConsulta() != EstadoConsulta.EXITO && conEvidencia.estadoConsulta() != EstadoConsulta.VACIA) {
            throw new ConsultaSenaceException(conEvidencia);
        }
        return conEvidencia;
    }

    private static List<Map<String, Object>> proyectosCompatibles(ResultadoConsultaSenace<ProyectoMapeado> resultado) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (ProyectoMapeado row : resultado.registros()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", row.id());
            poner(map, "titular", row.titular());
            poner(map, "ruc", row.ruc());
            poner(map, "titulo", row.tituloProyecto());
            poner(map, "proyecto", row.tituloProyecto());
            poner(map, "unidadProyecto", row.unidadProyecto());
            poner(map, "tipo", row.tipo());
            poner(map, "actividad", row.actividad());
            poner(map, "sector", row.actividad());
            poner(map, "fechaInicio", row.fechaInicio());
            poner(map, "anio", row.anioInicio());
            poner(map, "estado", row.estado());
            poner(map, "descripcion", row.descripcion());
            poner(map, "longitud", row.longitud());
            poner(map, "latitud", row.latitud());
            poner(map, "resolucion", row.resolucion());
            map.put("origen", resultado.origen().name());
            filas.add(map);
        }
        return filas;
    }

    private static void poner(Map<String, Object> map, String clave, Object valor) {
        if (valor != null) map.put(clave, valor);
    }

    public ResultadoConsultaSenace<ProyectoMapeado> fallbackConsultaProyectos(String sector, Integer anio, Throwable failure) {
        return ausencia(failure);
    }

    public ResultadoConsultaSenace<ConsultoraMapeada> fallbackConsultaConsultoras(Throwable failure) {
        return ausencia(failure);
    }

    public ResultadoConsultaSenace<ProyectoMapeado> fallbackCarteraCompleta(Throwable failure) {
        return ausencia(failure);
    }

    public List<Map<String, Object>> fallbackSenaceSimple(Throwable failure) {
        throw new ConsultaSenaceException(ausencia(failure));
    }

    public List<Map<String, Object>> fallbackSenace(String sector, Integer anio, Throwable failure) {
        throw new ConsultaSenaceException(ausencia(failure));
    }

    public List<Map<String, Object>> fallbackConsultoras(Throwable failure) {
        throw new ConsultaSenaceException(ausencia(failure));
    }

    public Map<String, Object> fallbackDatastreamGenerico(String nombre, Throwable failure) {
        ResultadoConsultaSenace<Object> consulta = ausencia(failure);
        Recurso recurso = resolverRecurso(nombre);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datastream", recurso == null ? "NO_RECONOCIDO" : recurso.nombre());
        result.put("origen", Origen.NO_DISPONIBLE.name());
        result.put("mensaje", "Servicio no disponible; no hay respaldo persistente implementado");
        result.put("datastreamsSoportados", DATASTREAMS_DISPONIBLES);
        result.put("consulta", metadatos(consulta));
        return result;
    }

    private static <T> ResultadoConsultaSenace<T> ausencia(Throwable failure) {
        ResultadoConsultaSenace<?> source = failure instanceof ConsultaSenaceException safe ? safe.resultado() : null;
        Motivo motivo = source != null ? source.motivo()
                : failure instanceof CallNotPermittedException ? Motivo.CIRCUITO_ABIERTO : Motivo.ERROR_EXTERNO;
        log.warn("Consulta SENACE no disponible; motivo={}", motivo);
        return new ResultadoConsultaSenace<>(List.of(), Origen.NO_DISPONIBLE,
                source != null ? source.estadoConsulta() : EstadoConsulta.ERROR_EXTERNO,
                source != null ? source.estadoFiltrado() : EstadoFiltrado.NO_APLICABLE, Cobertura.NO_VERIFICADA,
                source != null ? source.cuerpoCompleto() : null, motivo,
                source != null ? source.conteos() : Conteos.desconocidos(),
                source != null ? source.advertencias() : List.of());
    }

    private static ConsultaSenaceException fallo(EstadoConsulta estado, Motivo motivo, Boolean completo, Integer recibidos) {
        return new ConsultaSenaceException(new ResultadoConsultaSenace<>(List.of(), Origen.NO_DISPONIBLE,
                estado, EstadoFiltrado.NO_APLICABLE, Cobertura.NO_VERIFICADA, completo, motivo,
                new Conteos(recibidos, null, null, null, null, null), List.of()));
    }

    private static Map<String, Object> metadatos(ResultadoConsultaSenace<?> result) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("estado", result.estadoConsulta().name());
        meta.put("origen", result.origen().name());
        meta.put("estadoFiltrado", result.estadoFiltrado().name());
        meta.put("cobertura", result.cobertura().name());
        poner(meta, "cuerpoCompleto", result.cuerpoCompleto());
        if (result.motivo() != null) meta.put("motivo", result.motivo().name());
        meta.put("conteos", result.conteos());
        meta.put("advertencias", result.advertencias());
        return meta;
    }

    private static Recurso resolverRecurso(String nombre) {
        if (nombre == null || nombre.isBlank()) return Recurso.CARTERA_PROYECTOS;
        for (Recurso recurso : Recurso.values()) if (recurso.nombre().equalsIgnoreCase(nombre.trim())) return recurso;
        return null;
    }

    /** Sin causa, URI, cuerpo o texto externo. Solo metadatos seguros en el resultado de error. */
    public static final class ConsultaSenaceException extends RuntimeException {
        private final ResultadoConsultaSenace<?> resultado;
        private ConsultaSenaceException(ResultadoConsultaSenace<?> resultado) {
            super("Consulta SENACE no disponible: " + resultado.motivo(), null, false, false);
            this.resultado = resultado;
        }
        public ResultadoConsultaSenace<?> resultado() { return resultado; }
    }
}
