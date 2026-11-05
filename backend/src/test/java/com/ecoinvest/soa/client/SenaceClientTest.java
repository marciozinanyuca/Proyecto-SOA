package com.ecoinvest.soa.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoConsulta;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoFiltrado;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Motivo;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Origen;
import com.ecoinvest.soa.client.SenaceClient.ConsultaSenaceException;
import com.ecoinvest.soa.client.SenaceTransport.Estado;
import com.ecoinvest.soa.client.SenaceTransport.Recurso;
import com.ecoinvest.soa.client.SenaceTransport.RespuestaTransporte;
import com.ecoinvest.soa.client.SenaceTransport.TipoContenido;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Fixtures artificiales, transporte mock: sin HTTP, sockets o SQLite.
 * Solo los casos aop... crean contexto minimo con AOP/Resilience4j.
 * Sin component scanning, arranque Spring Boot o autoconfiguracion de persistencia.
 */
class SenaceClientTest {
    private static final String PROYECTOS = """
            [{"ID":9007199254740993,"TITULAR":"TITULAR ARTIFICIAL","RUC":"00000000001",
              "TITULO_PROYECTO":"PROYECTO ARTIFICIAL","UNIDAD_PROYECTO":"UNIDAD ARTIFICIAL",
              "TIPO":"TIPO PRUEBA","ACTIVIDAD":"Minería","FECHA_INICIO":"05/04/2017",
              "ESTADO":"Aprobado","DESCRIPCION":"SOLO FIXTURE",
              "LONGITUD":-70.12345678901234567890,"LATITUD":-12.12345678901234567890,
              "RESOLUCION":"RESOLUCION ARTIFICIAL"}]
            """;
    private static final String CONSULTORAS = """
            [{"RAZON_SOCIAL":"CONSULTORA ARTIFICIAL","ACTIVIDAD":"ACTIVIDAD PRUEBA",
              "SUBSECTOR":"SUBSECTOR PRUEBA","TIPO_SOLICITUD":"SOLICITUD PRUEBA",
              "RESOLUCION":"RESOLUCION ARTIFICIAL","FECHA_RD":"FECHA ORIGINAL",
              "INICIO_VIGENCIA_RD":"TEXTO ORIGINAL","FIN_VIGENCIA_RD":""}]
            """;
    private SenaceTransport transport;
    private ObjectMapper sharedJackson;
    private SenaceClient client;

    @BeforeEach void setUp() {
        transport = mock(SenaceTransport.class);
        sharedJackson = new ObjectMapper();
        client = new SenaceClient(transport, sharedJackson);
    }

    @Test void projectsPreserveThirteenFieldsPrecisionAndEvidence() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(PROYECTOS));
        var result = client.consultarProyectos(" MINERIA ", 2017);
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.EXITO);
        assertThat(result.origen()).isEqualTo(Origen.API_DIRECTA_SENACE);
        assertThat(result.cuerpoCompleto()).isTrue();
        assertThat(result.cobertura()).isEqualTo(ResultadoConsultaSenace.Cobertura.NO_VERIFICADA);
        assertThat(result.registros()).hasSize(1);
        var row = result.registros().get(0);
        assertThat(row.id()).isEqualTo(9007199254740993L);
        assertThat(row.ruc()).isEqualTo("00000000001");
        assertThat(row.actividad()).isEqualTo("Minería");
        assertThat(row.claveActividad()).isEqualTo("MINERIA");
        assertThat(row.fechaInicio()).isEqualTo("05/04/2017");
        assertThat(row.fechaInicioNormalizada()).hasToString("2017-04-05");
        assertThat(row.longitud()).isEqualByComparingTo("-70.12345678901234567890");
        assertThat(row.latitud()).isEqualByComparingTo("-12.12345678901234567890");
        assertThat(row.original().unidadProyecto()).isEqualTo("UNIDAD ARTIFICIAL");
        assertThat(row.original().resolucion()).isEqualTo("RESOLUCION ARTIFICIAL");
        assertThat(result.conteos().recibidos()).isEqualTo(1);
        assertThat(result.conteos().aprobados()).isEqualTo(1);
        verify(transport).consultar(Recurso.CARTERA_PROYECTOS);
    }

    @Test void consultantsKeepEightFieldsWithoutInventingIdentityOrStatus() {
        when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(recibida(CONSULTORAS));
        var result = client.consultarConsultoras();
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.EXITO);
        assertThat(result.cuerpoCompleto()).isTrue();
        var row = result.registros().get(0);
        assertThat(row.razonSocial()).isEqualTo("CONSULTORA ARTIFICIAL");
        assertThat(row.actividad()).isEqualTo("ACTIVIDAD PRUEBA");
        assertThat(row.subsector()).isEqualTo("SUBSECTOR PRUEBA");
        assertThat(row.tipoSolicitud()).isEqualTo("SOLICITUD PRUEBA");
        assertThat(row.resolucion()).isEqualTo("RESOLUCION ARTIFICIAL");
        assertThat(row.fechaRd()).isEqualTo("FECHA ORIGINAL");
        assertThat(row.inicioVigenciaRd()).isEqualTo("TEXTO ORIGINAL");
        assertThat(row.finVigenciaRd()).isNull();
        assertThat(row.original().finVigenciaRd()).isEmpty();
        assertThat(client.obtenerConsultoras().get(0)).doesNotContainKeys("ruc", "id", "registro", "nivel", "estado");
    }

    @ParameterizedTest
    @EnumSource(value = Recurso.class, names = {"CARTERA_PROYECTOS", "CONSULTORAS_AMBIENTALES"})
    void officialEmptyRetainsOriginAndKnownZeros(Recurso recurso) {
        when(transport.consultar(recurso)).thenReturn(recibida("[]"));
        ResultadoConsultaSenace<?> result = recurso == Recurso.CARTERA_PROYECTOS
                ? client.consultarProyectos("TODOS", 2017) : client.consultarConsultoras();
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.VACIA);
        assertThat(result.origen()).isEqualTo(Origen.API_DIRECTA_SENACE);
        assertThat(result.cuerpoCompleto()).isTrue();
        assertThat(result.conteos().recibidos()).isZero();
        assertThat(result.motivo()).isEqualTo(Motivo.RESPUESTA_OFICIAL_VACIA);
    }

    @Test void optionalFieldsAndBlanksDoNotInventProperties() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS))
                .thenReturn(recibida("[{\"ID\":1,\"FECHA_INICIO\":\"01/01/2017\",\"RUC\":\"  \"}]"));
        var result = client.consultarProyectos(null, null);
        assertThat(result.registros()).hasSize(1);
        assertThat(result.registros().get(0).ruc()).isNull();
        Map<String, Object> row = client.obtenerProyectos().get(0);
        assertThat(row).containsEntry("anio", 2017).containsEntry("id", 1L);
        assertThat(row).doesNotContainKeys("ruc", "inversion", "moneda", "expediente", "region", "latitud", "longitud");
    }

    @Test void emptyConsultantObjectDoesNotInventRequiredBusinessFields() {
        when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(recibida("[{}]"));
        assertThat(client.consultarConsultoras().registros()).hasSize(1);
        assertThat(client.obtenerConsultoras().get(0)).containsOnlyKeys("origen");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"1\"", "1.5", "1.0", "1e0", "true", "9223372036854775808", "[]"})
    void invalidIdRejectsWholeResponse(String id) {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS))
                .thenReturn(recibida("[{\"ID\":2},{\"ID\":" + id + "}]"));
        var result = failureOf(() -> client.consultarProyectos(null, null));
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
        assertThat(result.registros()).isEmpty();
        assertThat(result.origen()).isEqualTo(Origen.NO_DISPONIBLE);
        assertThat(result.cuerpoCompleto()).isTrue();
        assertThat(result.conteos().devueltos()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[1]", "[\"fila\"]", "[[]]", "[null]", "[{\"ID\":1},null]",
            "[] []", "[{", "<html>PRUEBA</html>"})
    void invalidRootOrRowIsNotOfficialEmpty(String json) {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(json));
        var result = failureOf(() -> client.consultarProyectos(null, 2017));
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
        assertThat(result.registros()).isEmpty();
        assertThat(result.conteos().aceptados()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[null]", "[1]", "[[]]", "[\"fila\"]", "{}"})
    void consultantsRejectStructurallyInvalidRows(String json) {
        when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(recibida(json));
        assertThat(failureOf(client::consultarConsultoras).estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"RUC\":123", "\"TITULAR\":false", "\"ACTIVIDAD\":1.2",
            "\"LONGITUD\":\"-70.1\"", "\"LATITUD\":\"\"", "\"ESTADO\":{\"x\":1}"})
    void fieldTypesAreNotSilentlyCoerced(String field) {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida("[{\"ID\":1," + field + "}]"));
        assertThat(failureOf(() -> client.consultarProyectos(null, 2017)).estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
    }

    @Test void mapperOwnsDatesFiltersAndQualityCounters() {
        String json = """
                [{"ID":1,"ACTIVIDAD":"  Minería  ","FECHA_INICIO":"05/04/2017","ESTADO":"Aprobado"},
                 {"ID":2,"ACTIVIDAD":"Minería","FECHA_INICIO":"31/02/2017"},
                 {"ID":3,"ACTIVIDAD":"Minería"},
                 {"ID":4,"ACTIVIDAD":"Minería","FECHA_INICIO":"01/01/2018"},
                 {"ID":5,"ACTIVIDAD":"Pesca","FECHA_INICIO":"01/01/2017"}]
                """;
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(json));
        var result = client.consultarProyectos("MINERIA", null);
        assertThat(result.registros()).hasSize(1);
        assertThat(result.registros().get(0).actividad()).isEqualTo("  Minería  ");
        assertThat(result.conteos().recibidos()).isEqualTo(5);
        assertThat(result.conteos().devueltos()).isEqualTo(1);
        assertThat(result.conteos().aprobados()).isEqualTo(1);
        assertThat(result.conteos().fechasNoEvaluables()).isEqualTo(2);
        assertThat(result.conteos().fechasNoEvaluablesEnActividad()).isEqualTo(2);
        assertThat(result.estadoFiltrado()).isEqualTo(EstadoFiltrado.NO_EVALUABLE);
        assertThat(result.motivo()).isEqualTo(Motivo.FECHAS_NO_EVALUABLES);
    }

    @Test void filterWithoutMatchesIsNotEmptyProviderAndDoesNotAliasSectors() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(PROYECTOS));
        var result = client.consultarProyectos("ENERGIA", 2017);
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.EXITO);
        assertThat(result.estadoFiltrado()).isEqualTo(EstadoFiltrado.SIN_COINCIDENCIAS);
        assertThat(result.origen()).isEqualTo(Origen.API_DIRECTA_SENACE);
        assertThat(result.conteos().recibidos()).isEqualTo(1);
        assertThat(result.registros()).isEmpty();
    }

    @Test void duplicateConsultantsAreRetainedWithoutDerivedValidity() {
        String row = CONSULTORAS.strip().substring(1, CONSULTORAS.strip().length() - 1);
        when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(recibida("[" + row + "," + row + "]"));
        assertThat(client.consultarConsultoras().registros()).hasSize(2);
        assertThat(client.obtenerConsultoras()).hasSize(2).allSatisfy(map ->
                assertThat(map).doesNotContainKeys("estado", "nivel", "ruc", "registro", "id"));
    }

    @Test void normalizedCoordinatesOmitInvalidPairAndKeepOriginals() {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(
                "[{\"ID\":1,\"FECHA_INICIO\":\"01/01/2017\",\"LATITUD\":91,\"LONGITUD\":-70}]"));
        var row = client.consultarProyectos("TODOS", 2017).registros().get(0);
        assertThat(row.latitud()).isNull();
        assertThat(row.longitud()).isNull();
        assertThat(row.original().latitud()).isEqualByComparingTo("91");
        assertThat(row.advertencias()).contains(ResultadoConsultaSenace.Advertencia.COORDENADAS_FUERA_DE_RANGO);
    }

    @ParameterizedTest
    @MethodSource("erroresTransporte")
    void transportCategoriesKeepSafeAbsenceAndUnknownCounts(Estado source, EstadoConsulta target, Motivo motivo) {
        when(transport.consultar(any())).thenReturn(new RespuestaTransporte(source, null,
                TipoContenido.DESCONOCIDO, null, 0, null, false, null));
        var result = failureOf(() -> client.consultarProyectos(null, null));
        assertThat(result.estadoConsulta()).isEqualTo(target);
        assertThat(result.motivo()).isEqualTo(motivo);
        assertThat(result.origen()).isEqualTo(Origen.NO_DISPONIBLE);
        assertThat(result.cuerpoCompleto()).isNull();
        assertThat(result.conteos().recibidos()).isNull();
        assertThat(result.conteos().aprobados()).isNull();
        assertThat(result.registros()).isEmpty();
    }

    static Stream<Arguments> erroresTransporte() {
        return Stream.of(
                Arguments.of(Estado.CONFIGURACION_AUSENTE, EstadoConsulta.ERROR_EXTERNO, Motivo.CONFIGURACION_AUSENTE),
                Arguments.of(Estado.CONFIGURACION_INVALIDA, EstadoConsulta.ERROR_EXTERNO, Motivo.CONFIGURACION_INVALIDA),
                Arguments.of(Estado.DESTINO_INVALIDO, EstadoConsulta.ERROR_EXTERNO, Motivo.DESTINO_INVALIDO),
                Arguments.of(Estado.RECURSO_INVALIDO, EstadoConsulta.ERROR_EXTERNO, Motivo.RECURSO_INVALIDO),
                Arguments.of(Estado.REDIRECCION_RECHAZADA, EstadoConsulta.ERROR_EXTERNO, Motivo.REDIRECCION_RECHAZADA),
                Arguments.of(Estado.ERROR_HTTP, EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_HTTP),
                Arguments.of(Estado.ERROR_TLS, EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_TLS),
                Arguments.of(Estado.ERROR_TRANSPORTE, EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_EXTERNO),
                Arguments.of(Estado.TIMEOUT_CONEXION, EstadoConsulta.ERROR_EXTERNO, Motivo.TIMEOUT_CONEXION),
                Arguments.of(Estado.TIMEOUT_INACTIVIDAD, EstadoConsulta.ERROR_EXTERNO, Motivo.TIMEOUT_INACTIVIDAD),
                Arguments.of(Estado.PLAZO_TOTAL_AGOTADO, EstadoConsulta.ERROR_EXTERNO, Motivo.PLAZO_TOTAL_AGOTADO),
                Arguments.of(Estado.LIMITE_TAMANO, EstadoConsulta.PARCIAL, Motivo.LIMITE_LECTURA),
                Arguments.of(Estado.RESPUESTA_INCOMPLETA, EstadoConsulta.PARCIAL, Motivo.RESPUESTA_PARCIAL),
                Arguments.of(Estado.HTML_INESPERADO, EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO),
                Arguments.of(Estado.JSON_INVALIDO, EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO),
                Arguments.of(Estado.ESTRUCTURA_INVALIDA, EstadoConsulta.FORMATO_INVALIDO, Motivo.FORMATO_INVALIDO),
                Arguments.of(Estado.CODIFICACION_NO_ADMITIDA, EstadoConsulta.FORMATO_INVALIDO, Motivo.CODIFICACION_NO_ADMITIDA),
                Arguments.of(Estado.INTERRUMPIDO, EstadoConsulta.ERROR_EXTERNO, Motivo.INTERRUMPIDO));
    }

    @Test void incompleteEvenParseableJsonIsNeverProcessed() {
        when(transport.consultar(any())).thenReturn(new RespuestaTransporte(Estado.RECIBIDO, 200,
                TipoContenido.JSON, "application/json", 2, null, false, "[]"));
        var result = failureOf(client::consultarConsultoras);
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.PARCIAL);
        assertThat(result.cuerpoCompleto()).isFalse();
        assertThat(result.conteos().recibidos()).isNull();
    }

    @Test void bodyCompletionComesFromTransportEvenOnErrors() {
        when(transport.consultar(any())).thenReturn(new RespuestaTransporte(Estado.ERROR_HTTP, 401,
                TipoContenido.DESCONOCIDO, "application/json", 20, 20L, true, null));
        assertThat(failureOf(client::consultarConsultoras).cuerpoCompleto()).isTrue();
        when(transport.consultar(any())).thenReturn(new RespuestaTransporte(Estado.TIMEOUT_INACTIVIDAD, 200,
                TipoContenido.DESCONOCIDO, "application/json", 4, null, false, null));
        var partial = failureOf(client::consultarConsultoras);
        assertThat(partial.cuerpoCompleto()).isFalse();
        assertThat(partial.estadoConsulta()).isEqualTo(EstadoConsulta.PARCIAL);
        assertThat(partial.motivo()).isEqualTo(Motivo.TIMEOUT_INACTIVIDAD);
    }

    @Test void missingTransportResultOrBodyIsNotOfficialEmpty() {
        assertThat(failureOf(client::consultarConsultoras).estadoConsulta()).isEqualTo(EstadoConsulta.ERROR_EXTERNO);
        when(transport.consultar(any())).thenReturn(new RespuestaTransporte(Estado.RECIBIDO, 200,
                TipoContenido.JSON, "application/json", 0, 0L, true, null));
        assertThat(failureOf(client::consultarConsultoras).estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
    }

    @Test void legacySignaturesAndPropertiesRemainCompatible() throws Exception {
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(PROYECTOS));
        when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(recibida(CONSULTORAS));
        List<Map<String, Object>> rows = client.obtenerProyectos("Mineria", 2017);
        assertThat(rows).hasSize(1).allSatisfy(map -> assertThat(map)
                .containsEntry("origen", "API_DIRECTA_SENACE").containsEntry("estado", "Aprobado")
                .doesNotContainKeys("inversion", "region", "moneda", "expediente"));
        assertThat(rows.get(0).get("latitud")).isInstanceOf(BigDecimal.class);
        assertThat(client.obtenerConsultoras()).hasSize(1);
        assertThat(SenaceClient.class.getMethod("obtenerProyectos", String.class, Integer.class).getReturnType()).isEqualTo(List.class);
        assertThat(SenaceClient.class.getMethod("obtenerProyectos").getReturnType()).isEqualTo(List.class);
        assertThat(SenaceClient.class.getMethod("obtenerConsultoras").getReturnType()).isEqualTo(List.class);
        assertThat(SenaceClient.class.getMethod("consultarDatastream", String.class).getReturnType()).isEqualTo(Map.class);
    }

    @Test void fallbacksNeverReturnSimulatedRowsOrSuccessfulErrorZeros() {
        RuntimeException unsafe = new RuntimeException("auth_key=MARCADOR_ARTIFICIAL_NO_PUBLICAR");
        for (Runnable query : List.<Runnable>of(() -> client.fallbackSenace("Mineria", 2017, unsafe),
                () -> client.fallbackSenaceSimple(unsafe), () -> client.fallbackConsultoras(unsafe))) {
            Throwable failure = catchThrowable(query::run);
            assertThat(failure).isInstanceOf(ConsultaSenaceException.class).hasNoCause();
            assertThat(failure.toString()).doesNotContain("MARCADOR_ARTIFICIAL", "auth_key", "SIMULADO");
        }
        var typed = client.fallbackConsultaConsultoras(unsafe);
        assertThat(typed.origen()).isEqualTo(Origen.NO_DISPONIBLE);
        assertThat(typed.registros()).isEmpty();
        assertThat(typed.conteos().recibidos()).isNull();
    }

    @Test void genericProjectsAreUnfilteredAndUseOfficialJsonNames() throws Exception {
        String json = "[{\"ID\":1,\"FECHA_INICIO\":\"01/01/2017\"},{\"ID\":2,\"FECHA_INICIO\":\"01/01/2020\"}]";
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(json));
        Map<String, Object> result = client.consultarDatastream(null);
        assertThat(result).containsEntry("datastream", "CarteraProyectos").containsEntry("origen", "API_DIRECTA_SENACE")
                .containsEntry("recursoValidado", true);
        var tree = sharedJackson.readTree(sharedJackson.writeValueAsString(result));
        assertThat(tree.get("data").size()).isEqualTo(2);
        assertThat(tree.get("data").get(1).get("ID").asInt()).isEqualTo(2);
        assertThat(tree.get("consulta").get("estadoFiltrado").asText()).isEqualTo("NO_APLICABLE");
    }

    @Test void genericConsultantsKeepAllRows() {
        when(transport.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(recibida("[{},{}]"));
        Map<String, Object> result = client.consultarDatastream(" consultorasambientales ");
        assertThat((List<?>) result.get("data")).hasSize(2);
        assertThat(result).containsEntry("recursoValidado", true);
    }

    @ParameterizedTest
    @EnumSource(value = Recurso.class, names = {"GASTO_ESPECIFICA", "GASTO_FUENTE", "GASTO_GENERICA", "SOLICITUD_ACCESO", "VISITAS", "RECLAMOS"})
    void legacyObjectsKeepStructureWithoutInventedMapping(Recurso recurso) {
        when(transport.consultar(recurso)).thenReturn(recibida("{\"datos\":[{\"valor\":0.12345678901234567890}]}"));
        Map<String, Object> result = client.consultarDatastream(recurso.nombre());
        assertThat(result).containsEntry("datastream", recurso.nombre()).containsEntry("recursoValidado", false);
        Map<?, ?> data = (Map<?, ?>) result.get("data");
        Map<?, ?> first = (Map<?, ?>) ((List<?>) data.get("datos")).get(0);
        assertThat(first.get("valor")).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) first.get("valor")).isEqualByComparingTo("0.12345678901234567890");
        verify(transport).consultar(recurso);
    }

    @Test void genericRejectsWrongRootAndInvalidRowsWithoutRemovingResources() {
        when(transport.consultar(Recurso.VISITAS)).thenReturn(recibida("[]"));
        assertThat(failureOf(() -> client.consultarDatastream("Visitas")).estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
        when(transport.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida("[{\"ID\":1},{}]"));
        assertThat(failureOf(() -> client.consultarDatastream("CarteraProyectos")).motivo()).isEqualTo(Motivo.ID_AUSENTE);
        assertThat(SenaceClient.DATASTREAMS_DISPONIBLES).hasSize(8).contains("Visitas", "Reclamos");
    }

    @Test void unknownResourceNeverPerformsHttpOrReflectsUnsafeInput() {
        String unsafe = "https://fixture.invalid?auth_key=MARCADOR_ARTIFICIAL";
        assertThat(failureOf(() -> client.consultarDatastream(unsafe)).motivo()).isEqualTo(Motivo.RECURSO_INVALIDO);
        var fallback = client.fallbackDatastreamGenerico(unsafe, new RuntimeException(unsafe));
        assertThat(fallback).containsEntry("datastream", "NO_RECONOCIDO").containsEntry("origen", "NO_DISPONIBLE");
        assertThat(fallback.toString()).doesNotContain("auth_key", "MARCADOR_ARTIFICIAL", "https://");
        verifyNoInteractions(transport);
    }

    @Test void localJacksonDoesNotChangeSharedCoercions() {
        var original = sharedJackson.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
                .findAction(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer);
        new SenaceClient(transport, sharedJackson);
        assertThat(sharedJackson.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
                .findAction(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer)).isEqualTo(original);
        assertThat(sharedJackson.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)).isFalse();
    }

    @Test void logsAndExceptionsExcludeExternalMessagesAndBodies() {
        Logger logger = (Logger) LoggerFactory.getLogger(SenaceClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(transport.consultar(any())).thenThrow(new RuntimeException("https://fixture.invalid?auth_key=MARCADOR_ARTIFICIAL"));
            var failure = catchThrowable(() -> client.consultarProyectos(null, null));
            assertThat(failure).isInstanceOf(ConsultaSenaceException.class).hasNoCause();
            client.fallbackConsultaProyectos(null, null, failure);
            client.fallbackConsultaConsultoras(failure);
            client.fallbackDatastreamGenerico("Visitas", failure);
            assertThat(failure.toString()).doesNotContain("MARCADOR_ARTIFICIAL", "auth_key", "https://");
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("MARCADOR_ARTIFICIAL", "auth_key", "https://");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void allProtectedMethodsHaveMatchingFallbackSignatures() throws Exception {
        for (Method method : SenaceClient.class.getMethods()) {
            CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);
            if (annotation == null) continue;
            assertThat(annotation.name()).isEqualTo("senace");
            Class<?>[] signature = Arrays.copyOf(method.getParameterTypes(), method.getParameterCount() + 1);
            signature[signature.length - 1] = Throwable.class;
            Method fallback = SenaceClient.class.getMethod(annotation.fallbackMethod(), signature);
            assertThat(fallback.getReturnType()).isEqualTo(method.getReturnType());
        }
    }

    @Test void aopInterceptsSuccessesWithoutDoubleCounting() {
        aopContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
            SenaceTransport mocked = context.getBean(SenaceTransport.class);
            when(mocked.consultar(Recurso.CARTERA_PROYECTOS)).thenReturn(recibida(PROYECTOS));
            when(mocked.consultar(Recurso.CONSULTORAS_AMBIENTALES)).thenReturn(recibida(CONSULTORAS));
            SenaceClient proxy = context.getBean(SenaceClient.class);
            assertThat(AopUtils.isAopProxy(proxy)).isTrue();
            var breaker = context.getBean(CircuitBreakerRegistry.class).circuitBreaker("senace");
            AtomicInteger successes = new AtomicInteger();
            breaker.getEventPublisher().onSuccess(event -> successes.incrementAndGet());
            assertThat(proxy.consultarProyectos("MINERIA", 2017).registros()).hasSize(1);
            assertThat(proxy.obtenerProyectos()).hasSize(1);
            assertThat(proxy.obtenerProyectos("MINERIA", 2017)).hasSize(1);
            assertThat(proxy.consultarConsultoras().registros()).hasSize(1);
            assertThat(proxy.obtenerConsultoras()).hasSize(1);
            assertThat(successes).hasValue(5);
            verify(mocked, times(3)).consultar(Recurso.CARTERA_PROYECTOS);
            verify(mocked, times(2)).consultar(Recurso.CONSULTORAS_AMBIENTALES);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"typedProjects", "typedConsultants", "legacyProjects", "legacyDefault", "legacyConsultants", "generic"})
    void aopExecutesFallbacksAndRecordsFailures(String method) {
        aopContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
            SenaceTransport mocked = context.getBean(SenaceTransport.class);
            when(mocked.consultar(any())).thenReturn(new RespuestaTransporte(Estado.CONFIGURACION_AUSENTE,
                    null, TipoContenido.DESCONOCIDO, null, 0, null, false, null));
            SenaceClient proxy = context.getBean(SenaceClient.class);
            var breaker = context.getBean(CircuitBreakerRegistry.class).circuitBreaker("senace");
            switch (method) {
                case "typedProjects" -> assertThat(proxy.consultarProyectos(null, null).motivo()).isEqualTo(Motivo.CONFIGURACION_AUSENTE);
                case "typedConsultants" -> assertThat(proxy.consultarConsultoras().motivo()).isEqualTo(Motivo.CONFIGURACION_AUSENTE);
                case "legacyProjects" -> assertThatThrownBy(() -> proxy.obtenerProyectos(null, null)).isInstanceOf(ConsultaSenaceException.class);
                case "legacyDefault" -> assertThatThrownBy(proxy::obtenerProyectos).isInstanceOf(ConsultaSenaceException.class);
                case "legacyConsultants" -> assertThatThrownBy(proxy::obtenerConsultoras).isInstanceOf(ConsultaSenaceException.class);
                case "generic" -> assertThat(proxy.consultarDatastream("Visitas")).containsEntry("origen", "NO_DISPONIBLE");
                default -> throw new AssertionError("CASO_DESCONOCIDO");
            }
            assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
            verify(mocked).consultar(any());
        });
    }

    @Test void aopOpenCircuitReturnsReasonWithoutAdditionalTransportCall() {
        aopContext().run(context -> {
            assertThat(context).hasNotFailed();
            SenaceTransport mocked = context.getBean(SenaceTransport.class);
            when(mocked.consultar(any())).thenReturn(new RespuestaTransporte(Estado.ERROR_HTTP, 503,
                    TipoContenido.DESCONOCIDO, null, 0, 0L, true, null));
            SenaceClient proxy = context.getBean(SenaceClient.class);
            var breaker = context.getBean(CircuitBreakerRegistry.class).circuitBreaker("senace");
            int effective = Math.min(breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                    breaker.getCircuitBreakerConfig().getSlidingWindowSize());
            for (int i = 0; i < effective; i++) {
                assertThat(proxy.consultarConsultoras().motivo()).isEqualTo(Motivo.ERROR_HTTP);
            }
            assertThat(breaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
            assertThat(proxy.consultarConsultoras().motivo()).isEqualTo(Motivo.CIRCUITO_ABIERTO);
            verify(mocked, times(effective)).consultar(any());
        });
    }

    private static ApplicationContextRunner aopContext() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class))
                .withUserConfiguration(AopBeans.class)
                .withInitializer(context -> {
                    try {
                        var sources = new YamlPropertySourceLoader().load("actual-config", new ClassPathResource("application.yml"));
                        sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("CONFIGURACION_PRUEBA_NO_DISPONIBLE");
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class AopBeans {
        @Bean SenaceTransport transport() { return mock(SenaceTransport.class); }
        @Bean ObjectMapper jackson() { return new ObjectMapper(); }
        @Bean SenaceClient senaceClient(SenaceTransport transport, ObjectMapper jackson) {
            return new SenaceClient(transport, jackson);
        }
    }

    private static RespuestaTransporte recibida(String json) {
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        return new RespuestaTransporte(Estado.RECIBIDO, 200, TipoContenido.JSON, "application/json", bytes, (long) bytes, true, json);
    }

    private static ResultadoConsultaSenace<?> failureOf(Runnable query) {
        Throwable failure = catchThrowable(query::run);
        assertThat(failure).isInstanceOf(ConsultaSenaceException.class).hasNoCause();
        return ((ConsultaSenaceException) failure).resultado();
    }
}
