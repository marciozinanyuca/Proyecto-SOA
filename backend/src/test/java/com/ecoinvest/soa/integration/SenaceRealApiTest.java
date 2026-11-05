package com.ecoinvest.soa.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.ecoinvest.soa.client.ResultadoConsultaSenace;
import com.ecoinvest.soa.client.SenaceClient;
import com.ecoinvest.soa.client.SenaceMapper;
import com.ecoinvest.soa.client.SenaceTransport;
import com.ecoinvest.soa.client.dto.ConsultoraSenaceDto;
import com.ecoinvest.soa.client.dto.ProyectoSenaceDto;
import com.ecoinvest.soa.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Opt-in exclusivamente manual. No Spring Context, Mockito, servidor o persistencia.
 * Una seleccion exacta de metodo + permisos explicitos preceden toda lectura de
 * configuracion y toda consulta. No conserva cuerpos ni imprime filas o causas.
 * Es una llamada directa: NO acredita interceptacion AOP.
 */
@EnabledIfSystemProperty(named = "ecoinvest.test.senace-real", matches = "true")
class SenaceRealApiTest {
    private static final String HOST = "datosabiertos.senace.gob.pe";
    private static final String PREFIX = "ecoinvest.test.senace-";

    @Test void validarCarteraProyectos() {
        execute("validarCarteraProyectos", "CarteraProyectos", client -> {
            Map<String, Object> result = query(client, "CarteraProyectos");
            List<ProyectoSenaceDto> rows = typedRows(result, ProyectoSenaceDto.class);
            SenaceMapper mapper = new SenaceMapper();
            TreeMap<Integer, Long> years = new TreeMap<>();
            Map<String, Long> dates = new TreeMap<>();
            HashSet<Long> ids = new HashSet<>();
            long duplicateIds = 0;
            long invalidCoordinates = 0;
            for (ProyectoSenaceDto row : rows) {
                require(row.id() != null, "ID_ESENCIAL_AUSENTE");
                if (!ids.add(row.id())) duplicateIds++;
                var mapped = mapper.mapearProyecto(row);
                require(mapped.original() == row, "ORIGINAL_PROYECTO_NO_CONSERVADO");
                require(Objects.equals(mapped.id(), row.id()), "ID_NO_CONSERVADO");
                require(Objects.equals(mapped.ruc(), optional(row.ruc())), "RUC_NO_CONSERVADO");
                require(Objects.equals(mapped.titular(), optional(row.titular())), "TITULAR_NO_CONSERVADO");
                require(Objects.equals(mapped.tituloProyecto(), optional(row.tituloProyecto())), "TITULO_NO_CONSERVADO");
                require(Objects.equals(mapped.unidadProyecto(), optional(row.unidadProyecto())), "UNIDAD_NO_CONSERVADA");
                require(Objects.equals(mapped.tipo(), optional(row.tipo())), "TIPO_NO_CONSERVADO");
                require(Objects.equals(mapped.actividad(), row.actividad()), "ACTIVIDAD_NO_CONSERVADA");
                require(Objects.equals(mapped.fechaInicio(), row.fechaInicio()), "FECHA_ORIGINAL_NO_CONSERVADA");
                require(Objects.equals(mapped.estado(), optional(row.estado())), "ESTADO_NO_CONSERVADO");
                require(Objects.equals(mapped.descripcion(), optional(row.descripcion())), "DESCRIPCION_NO_CONSERVADA");
                require(Objects.equals(mapped.resolucion(), optional(row.resolucion())), "RESOLUCION_NO_CONSERVADA");
                if (mapped.latitud() != null) {
                    sameDecimal(mapped.latitud(), row.latitud());
                    sameDecimal(mapped.longitud(), row.longitud());
                } else if (row.latitud() != null || row.longitud() != null) invalidCoordinates++;
                dates.merge(mapped.estadoFecha().name(), 1L, Long::sum);
                if (mapped.anioInicio() != null) years.merge(mapped.anioInicio(), 1L, Long::sum);
                else require(mapped.fechaInicioNormalizada() == null, "FECHA_NO_EVALUABLE_CON_ANIO_INVENTADO");
            }
            System.out.println("SENACE proyectos: registros=" + rows.size() + "; idsRepetidos=" + duplicateIds
                    + "; fechas=" + dates + "; distribucionAnioInicio=" + years
                    + "; coordenadasNoUtilizables=" + invalidCoordinates);
            profileProjects(rows);

            // Reutiliza los DTOs descargados: NO realiza una segunda consulta.
            var filtered = mapper.mapearProyectos(rows, "MINERIA", 2017);
            long expected = rows.stream().map(mapper::mapearProyecto)
                    .filter(p -> Integer.valueOf(2017).equals(p.anioInicio()) && "MINERIA".equals(p.claveActividad())).count();
            long approved = filtered.registros().stream().filter(p -> "Aprobado".equalsIgnoreCase(p.estado())).count();
            require(filtered.registros().size() == expected, "FILTRO_ACTIVIDAD_ANIO_INCONSISTENTE");
            require(Objects.equals(filtered.conteos().aprobados(), (int) approved), "CONTEO_APROBADOS_INCONSISTENTE");
            require(Objects.equals(filtered.conteos().recibidos(), rows.size()), "CONTEO_RECIBIDOS_INCONSISTENTE");
            require(filtered.cuerpoCompleto() == null, "MAPPER_NO_DEBE_FABRICAR_EVIDENCIA_HTTP");
            System.out.println("SENACE filtro SOLO EN MEMORIA: actividad=MINERIA; anioInicio=2017; registros="
                    + filtered.registros().size() + "; aprobados=" + approved + "; estadoFiltrado="
                    + filtered.estadoFiltrado() + "; fechasNoEvaluables=" + filtered.conteos().fechasNoEvaluables());
        });
    }

    @Test void validarConsultorasAmbientales() {
        execute("validarConsultorasAmbientales", "ConsultorasAmbientales", client -> {
            Map<String, Object> result = query(client, "ConsultorasAmbientales");
            List<ConsultoraSenaceDto> rows = typedRows(result, ConsultoraSenaceDto.class);
            var mapped = new SenaceMapper().mapearConsultoras(rows);
            require(mapped.registros().size() == rows.size(), "FILAS_CONSULTORAS_NO_CONSERVADAS");
            require(Objects.equals(mapped.conteos().recibidos(), rows.size()), "CONTEO_CONSULTORAS_INCONSISTENTE");
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                var target = mapped.registros().get(i);
                require(target.original() == row, "ORDEN_ORIGINAL_CONSULTORA_NO_CONSERVADO");
                require(Objects.equals(target.razonSocial(), optional(row.razonSocial())), "RAZON_SOCIAL_NO_CONSERVADA");
                require(Objects.equals(target.actividad(), row.actividad()), "ACTIVIDAD_NO_CONSERVADA");
                require(Objects.equals(target.subsector(), optional(row.subsector())), "SUBSECTOR_NO_CONSERVADO");
                require(Objects.equals(target.tipoSolicitud(), optional(row.tipoSolicitud())), "TIPO_SOLICITUD_NO_CONSERVADO");
                require(Objects.equals(target.resolucion(), optional(row.resolucion())), "RESOLUCION_NO_CONSERVADA");
                require(Objects.equals(target.fechaRd(), optional(row.fechaRd())), "FECHA_RD_NO_CONSERVADA");
                require(Objects.equals(target.inicioVigenciaRd(), optional(row.inicioVigenciaRd())), "INICIO_VIGENCIA_NO_CONSERVADO");
                require(Objects.equals(target.finVigenciaRd(), optional(row.finVigenciaRd())), "FIN_VIGENCIA_NO_CONSERVADO");
            }
            // Igualdad de las ocho columnas, no identidad empresarial ni clave oficial.
            long repeated = rows.size() - new HashSet<>(rows).size();
            System.out.println("SENACE consultoras: filas=" + rows.size() + "; repeticionesExactasOchoCampos=" + repeated
                    + "; filasMapeadas=" + mapped.registros().size() + "; vigenciaDerivada=NO");
            Map<String, Function<ConsultoraSenaceDto, Object>> fields = new LinkedHashMap<>();
            fields.put("RAZON_SOCIAL", ConsultoraSenaceDto::razonSocial);
            fields.put("ACTIVIDAD", ConsultoraSenaceDto::actividad);
            fields.put("SUBSECTOR", ConsultoraSenaceDto::subsector);
            fields.put("TIPO_SOLICITUD", ConsultoraSenaceDto::tipoSolicitud);
            fields.put("RESOLUCION", ConsultoraSenaceDto::resolucion);
            fields.put("FECHA_RD", ConsultoraSenaceDto::fechaRd);
            fields.put("INICIO_VIGENCIA_RD", ConsultoraSenaceDto::inicioVigenciaRd);
            fields.put("FIN_VIGENCIA_RD", ConsultoraSenaceDto::finVigenciaRd);
            profile(rows, fields);
        });
    }

    private static void execute(String method, String resource, Inspection inspection) {
        // Incluso si alguien habilita la clase completa, no autoriza dos GET por accidente.
        String selection = System.getProperty("test", "");
        require(selection.equals("SenaceRealApiTest#" + method)
                || selection.equals(SenaceRealApiTest.class.getName() + "#" + method), "SELECCION_EXACTA_DE_METODO_REQUERIDA");
        require(Boolean.getBoolean(PREFIX + "credentials"), "ACCESO_CONFIGURACION_NO_AUTORIZADO");
        require(resource.equals(System.getProperty(PREFIX + "resource")), "RECURSO_HTTP_NO_AUTORIZADO");
        require("17.0.12".equals(System.getProperty("java.version"))
                && "Oracle Corporation".equals(System.getProperty("java.vendor")), "ORACLE_JDK_17_0_12_REQUERIDO");
        try (QuietHttpLogs ignored = new QuietHttpLogs()) {
            Configuration config = configuration();
            ObjectMapper jackson = Jackson2ObjectMapperBuilder.json()
                    .modulesToInstall(new AppConfig().jsonNullableModule()).build();
            SenaceTransport transport = new SenaceTransport(config.base(), config.key());
            SenaceClient client = new SenaceClient(transport, jackson);
            // No contexto Spring: no DataSource/JPA/servidor ni AppConfig completo.
            inspection.inspect(client);
        } catch (SenaceClient.ConsultaSenaceException safe) {
            var result = safe.resultado();
            throw new AssertionError("SENACE_NO_VALIDADO: estado=" + result.estadoConsulta()
                    + "; motivo=" + result.motivo() + "; cuerpoCompleto=" + result.cuerpoCompleto());
        } catch (RuntimeException unsafe) {
            // No causa, mensaje o stack externo: pueden contener URI, credencial o filas.
            throw new AssertionError("FALLO_LOCAL_SEGURO_SIN_DETALLES_EXTERNOS");
        }
    }

    private static Map<String, Object> query(SenaceClient client, String resource) {
        Map<String, Object> result = client.consultarDatastream(resource); // UNICA consulta por metodo.
        require(resource.equals(result.get("datastream")), "RECURSO_INCONSISTENTE");
        require("API_DIRECTA_SENACE".equals(result.get("origen")), "PROCEDENCIA_NO_OFICIAL");
        require(Boolean.TRUE.equals(result.get("recursoValidado")), "RECURSO_NO_VALIDADO");
        require(result.get("consulta") instanceof Map<?, ?>, "METADATOS_AUSENTES");
        Map<?, ?> meta = (Map<?, ?>) result.get("consulta");
        require("EXITO".equals(meta.get("estado")) || "VACIA".equals(meta.get("estado")), "CONSULTA_NO_UTILIZABLE");
        require(Boolean.TRUE.equals(meta.get("cuerpoCompleto")), "CUERPO_INCOMPLETO");
        require("NO_VERIFICADA".equals(meta.get("cobertura")), "COBERTURA_INCONSISTENTE");
        require(meta.get("conteos") instanceof ResultadoConsultaSenace.Conteos, "CONTEOS_AUSENTES");
        require(result.get("data") instanceof List<?>, "RAIZ_ARRAY_REQUERIDA");
        int count = ((List<?>) result.get("data")).size();
        var counts = (ResultadoConsultaSenace.Conteos) meta.get("conteos");
        require(Objects.equals(counts.recibidos(), count) && Objects.equals(counts.devueltos(), count), "CONTEOS_INCONSISTENTES");
        System.out.println("SENACE recurso=" + resource + "; estado=" + meta.get("estado")
                + "; origen=API_DIRECTA_SENACE; cuerpoCompleto=true; cobertura=NO_VERIFICADA; registros=" + count);
        System.out.println("HTTP=200 INFERIDO por contrato recibir(); JSON array validado por transporte/cliente; "
                + "Content-Type original y codigo HTTP de errores NO expuestos por SenaceClient. "
                + "No acredita que auth_key sea obligatoria ni cobertura total del dataset.");
        return result;
    }

    private static <T> List<T> typedRows(Map<String, Object> result, Class<T> type) {
        List<T> rows = new ArrayList<>();
        for (Object row : (List<?>) result.get("data")) {
            require(type.isInstance(row), "FILA_DTO_INCOMPATIBLE");
            rows.add(type.cast(row));
        }
        return rows;
    }

    private static void profileProjects(List<ProyectoSenaceDto> rows) {
        Map<String, Function<ProyectoSenaceDto, Object>> fields = new LinkedHashMap<>();
        fields.put("ID", ProyectoSenaceDto::id);
        fields.put("TITULAR", ProyectoSenaceDto::titular);
        fields.put("RUC", ProyectoSenaceDto::ruc);
        fields.put("TITULO_PROYECTO", ProyectoSenaceDto::tituloProyecto);
        fields.put("UNIDAD_PROYECTO", ProyectoSenaceDto::unidadProyecto);
        fields.put("TIPO", ProyectoSenaceDto::tipo);
        fields.put("ACTIVIDAD", ProyectoSenaceDto::actividad);
        fields.put("FECHA_INICIO", ProyectoSenaceDto::fechaInicio);
        fields.put("ESTADO", ProyectoSenaceDto::estado);
        fields.put("DESCRIPCION", ProyectoSenaceDto::descripcion);
        fields.put("LONGITUD", ProyectoSenaceDto::longitud);
        fields.put("LATITUD", ProyectoSenaceDto::latitud);
        fields.put("RESOLUCION", ProyectoSenaceDto::resolucion);
        profile(rows, fields);
    }

    private static <T> void profile(List<T> rows, Map<String, Function<T, Object>> fields) {
        // Ausente y null JSON no se distinguen despues del DTO; no prometer presencia original.
        fields.forEach((name, accessor) -> {
            long nulls = 0, blanks = 0;
            Map<String, Long> types = new TreeMap<>();
            for (T row : rows) {
                Object value = accessor.apply(row);
                if (value == null) nulls++;
                else {
                    types.merge(value.getClass().getSimpleName(), 1L, Long::sum);
                    if (value instanceof String text && text.isBlank()) blanks++;
                }
            }
            System.out.println("SENACE campo=" + name + "; tiposDTO=" + types + "; ausenteONull=" + nulls + "; blancos=" + blanks);
        });
    }

    private static Configuration configuration() {
        String source = System.getProperty(PREFIX + "config", "");
        Map<String, String> values = new LinkedHashMap<>();
        if (source.equals("environment")) {
            for (String key : List.of("SENACE_BASE_URL", "SENACE_AUTH_KEY")) values.put(key, System.getenv(key));
        } else if (source.equals("local")) {
            Path file = Path.of(".env.apis.local"); // Ejecucion acordada desde backend/.
            require(Files.isRegularFile(file), "CONFIGURACION_LOCAL_NO_DISPONIBLE");
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int equals = line.indexOf('=');
                    if (equals < 1) continue;
                    String key = line.substring(0, equals).trim();
                    if (!key.equals("SENACE_BASE_URL") && !key.equals("SENACE_AUTH_KEY")) continue;
                    require(!values.containsKey(key), "CLAVE_CONFIGURACION_DUPLICADA");
                    String value = line.substring(equals + 1).trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
                    values.put(key, value); // Texto literal: sin expansion ni ejecucion.
                }
            } catch (IOException exception) {
                throw new AssertionError("CONFIGURACION_LOCAL_NO_LEIBLE");
            }
        } else throw new AssertionError("ORIGEN_CONFIGURACION_EXPLICITO_REQUERIDO");
        String base = values.get("SENACE_BASE_URL");
        String key = values.get("SENACE_AUTH_KEY");
        require(base != null && !base.isBlank() && key != null && !key.isBlank(), "CONFIGURACION_INCOMPLETA");
        URI uri = URI.create(base);
        require("https".equalsIgnoreCase(uri.getScheme()) && HOST.equalsIgnoreCase(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null
                && uri.getRawQuery() == null && uri.getRawFragment() == null
                && ("/Api/datastreams".equals(uri.getPath()) || "/Api/datastreams/".equals(uri.getPath())), "DESTINO_NO_AUTORIZADO");
        require(key.chars().noneMatch(Character::isISOControl), "CONFIGURACION_INVALIDA");
        return new Configuration(base, key);
    }

    private record Configuration(String base, String key) {
        @Override public String toString() { return "Configuration[VALORES_OMITIDOS]"; }
    }

    /** Bloquea logs Apache incluso sin cargar logging.* de application.yml y restaura al salir. */
    private static final class QuietHttpLogs implements AutoCloseable {
        private final Map<ch.qos.logback.classic.Logger, Level> previous = new LinkedHashMap<>();
        QuietHttpLogs() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            for (String name : List.of("org.apache.hc.client5.http", "org.apache.hc.core5.http")) {
                var logger = context.getLogger(name);
                previous.put(logger, logger.getLevel());
                logger.setLevel(Level.OFF);
            }
            for (var logger : context.getLoggerList()) {
                if (logger.getName().startsWith("org.apache.hc.")) {
                    if (!previous.containsKey(logger)) previous.put(logger, logger.getLevel());
                    logger.setLevel(Level.OFF);
                }
            }
        }
        @Override public void close() { previous.forEach(ch.qos.logback.classic.Logger::setLevel); }
    }

    @FunctionalInterface private interface Inspection { void inspect(SenaceClient client); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value; }
    private static void sameDecimal(BigDecimal actual, BigDecimal original) {
        require(actual != null && original != null && actual.equals(original), "PRECISION_DECIMAL_NO_CONSERVADA");
    }
    private static void require(boolean condition, String safeMessage) {
        if (!condition) throw new AssertionError(safeMessage);
    }
}
