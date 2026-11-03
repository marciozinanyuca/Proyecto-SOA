package com.ecoinvest.soa.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecoinvest.soa.client.ResultadoConsultaSenace.Advertencia;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Cobertura;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Conteos;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoConsulta;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoFiltrado;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Motivo;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Origen;
import com.ecoinvest.soa.client.SenaceMapper.EstadoFecha;
import com.ecoinvest.soa.client.dto.ConsultoraSenaceDto;
import com.ecoinvest.soa.client.dto.ProyectoSenaceDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Fixtures artificiales: nombres y numeros no representan observaciones SENACE.
 * Sin Spring Context, transporte, SQLite o lectura de configuracion.
 */
class SenaceMapperTest {
    private final SenaceMapper mapper = new SenaceMapper();
    private final ObjectMapper jackson = new ObjectMapper();

    @Test
    void projectJsonUsesThirteenOfficialPropertiesAndKeepsExactNumbers() throws Exception {
        String json = """
                {"ID":9223372036854775807,"TITULAR":"ENTIDAD FICTICIA DE PRUEBA",
                 "RUC":"00000000001","TITULO_PROYECTO":"Titulo artificial",
                 "UNIDAD_PROYECTO":"Unidad artificial","TIPO":"TIPO_PRUEBA",
                 "ACTIVIDAD":"Minería","FECHA_INICIO":"05/04/2017","ESTADO":"Aprobado",
                 "DESCRIPCION":"Descripcion artificial",
                 "LONGITUD":-75.123456789012345678901234567890,
                 "LATITUD":-12.123456789012345678901234567890,"RESOLUCION":" Resolucion de prueba "}
                """;
        ProyectoSenaceDto dto = jackson.readValue(json, ProyectoSenaceDto.class);
        assertThat(dto.id()).isEqualTo(Long.MAX_VALUE);
        assertThat(dto.ruc()).isEqualTo("00000000001");
        assertThat(dto.longitud()).isEqualTo(new BigDecimal("-75.123456789012345678901234567890"));
        assertThat(dto.latitud()).isEqualTo(new BigDecimal("-12.123456789012345678901234567890"));
        assertThat(dto.titular()).isEqualTo("ENTIDAD FICTICIA DE PRUEBA");
        assertThat(dto.unidadProyecto()).isEqualTo("Unidad artificial");
        assertThat(dto.tipo()).isEqualTo("TIPO_PRUEBA");
        assertThat(dto.descripcion()).isEqualTo("Descripcion artificial");
        JsonNode encoded = jackson.valueToTree(dto);
        assertThat(encoded.size()).isEqualTo(13);
        assertThat(encoded.has("ID")).isTrue();
        assertThat(encoded.has("FECHA_INICIO")).isTrue();
        assertThat(encoded.has("LONGITUD")).isTrue();
        assertThat(encoded.has("id")).isFalse();
        SenaceMapper.ProyectoMapeado mapped = mapper.mapearProyecto(dto);
        assertThat(mapped.original()).isSameAs(dto);
        assertThat(mapped.id()).isEqualTo(dto.id());
        assertThat(mapped.ruc()).isEqualTo(dto.ruc());
        assertThat(mapped.tituloProyecto()).isEqualTo(dto.tituloProyecto());
        assertThat(mapped.resolucion()).isEqualTo(" Resolucion de prueba ");
        assertThat(mapped.longitud()).isEqualTo(dto.longitud());
        assertThat(mapped.latitud()).isEqualTo(dto.latitud());
        assertThat(mapped.fechaInicio()).isEqualTo("05/04/2017");
        assertThat(mapped.fechaInicioNormalizada()).isEqualTo(LocalDate.of(2017, 4, 5));
        assertThat(mapped.anioInicio()).isEqualTo(2017);
        assertThat(mapped.estadoFecha()).isEqualTo(EstadoFecha.VALIDA);
        assertThat(mapped.advertencias()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1.0", "1e0", "9223372036854775808", "-9223372036854775809", "\"1\"", "true", "[]"})
    void projectIdRejectsCoercionAndOverflow(String token) {
        assertThatThrownBy(() -> jackson.readValue("{\"ID\":" + token + "}", ProyectoSenaceDto.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE})
    void nonNullIntegralIdDoesNotAcquireAnUnapprovedPositivityRule(long id) {
        assertThat(mapper.mapearProyecto(project(id, "Minería", "01/01/2017", "Aprobado")).id()).isEqualTo(id);
    }

    @Test
    void missingIdAndNullRowAreExplicitStructuralErrorsWithoutPartialPublication() throws Exception {
        ProyectoSenaceDto missing = jackson.readValue("{}", ProyectoSenaceDto.class);
        assertThatThrownBy(() -> mapper.mapearProyecto(missing)).hasMessage("ID_AUSENTE");
        assertThatThrownBy(() -> mapper.mapearProyecto(null)).hasMessage("FILA_NULA");
        var invalid = mapper.mapearProyectos(List.of(project(1L, "Minería", "01/01/2017", "Aprobado"), missing), "TODOS", 2017);
        assertThat(invalid.estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
        assertThat(invalid.motivo()).isEqualTo(Motivo.ID_AUSENTE);
        assertThat(invalid.registros()).isEmpty();
        assertThat(invalid.conteos().recibidos()).isEqualTo(2);
        assertThat(invalid.conteos().aceptados()).isNull();
        assertThat(invalid.conteos().aprobados()).isNull();
        assertThat(mapper.mapearProyectos(Arrays.asList(project(1L, "Pesca", "01/01/2017", "Otros"), null), "TODOS", 2017).motivo())
                .isEqualTo(Motivo.FILA_NULA);
    }

    @Test
    void optionalFieldsMayBeAbsentWithoutInventingValues() throws Exception {
        ProyectoSenaceDto dto = jackson.readValue("{\"ID\":1}", ProyectoSenaceDto.class);
        var mapped = mapper.mapearProyecto(dto);
        assertThat(mapped.ruc()).isNull();
        assertThat(mapped.titular()).isNull();
        assertThat(mapped.resolucion()).isNull();
        assertThat(mapped.longitud()).isNull();
        assertThat(mapped.latitud()).isNull();
        assertThat(mapped.fechaInicioNormalizada()).isNull();
        assertThat(mapped.estadoFecha()).isEqualTo(EstadoFecha.AUSENTE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void emptyProjectStringsBecomeAbsentOnlyInNormalizedProperties(String value) {
        ProyectoSenaceDto dto = new ProyectoSenaceDto(1L, value, value, value, value,
                value, value, value, value, value, null, null, value);
        var mapped = mapper.mapearProyecto(dto);
        assertThat(mapped.original().ruc()).isEqualTo(value);
        assertThat(mapped.actividad()).isEqualTo(value);
        assertThat(mapped.fechaInicio()).isEqualTo(value);
        assertThat(mapped.titular()).isNull();
        assertThat(mapped.ruc()).isNull();
        assertThat(mapped.tituloProyecto()).isNull();
        assertThat(mapped.resolucion()).isNull();
        assertThat(mapped.claveActividad()).isNull();
        assertThat(mapped.estadoFecha()).isEqualTo(EstadoFecha.AUSENTE);
    }

    @ParameterizedTest
    @CsvSource({"05/04/2017,2017-04-05", "01/12/2018,2018-12-01", "29/02/2020,2020-02-29", "31/12/2017,2017-12-31"})
    void datesUseApprovedDayMonthOrderIncludingAmbiguousCases(String raw, String expected) {
        var mapped = mapper.mapearProyecto(project(1L, "Minería", raw, "Aprobado"));
        assertThat(mapped.fechaInicio()).isEqualTo(raw);
        assertThat(mapped.fechaInicioNormalizada()).isEqualTo(LocalDate.parse(expected));
        assertThat(mapped.estadoFecha()).isEqualTo(EstadoFecha.VALIDA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/02/2017", "29/02/2019", "04/13/2017", "2017-04-05", "5/4/2017", "05/04/17", "texto", " 05/04/2017 "})
    void invalidDatesRemainOriginalAndNeverReceiveTheRequestedYear(String raw) {
        var mapped = mapper.mapearProyecto(project(1L, "Minería", raw, "Aprobado"));
        assertThat(mapped.fechaInicio()).isEqualTo(raw);
        assertThat(mapped.fechaInicioNormalizada()).isNull();
        assertThat(mapped.anioInicio()).isNull();
        assertThat(mapped.estadoFecha()).isEqualTo(EstadoFecha.INVALIDA);
        assertThat(mapped.advertencias()).containsExactly(Advertencia.FECHA_INVALIDA);
    }

    @Test
    void activityComparisonPreservesOriginalAndDoesNotTranslateSectors() {
        String raw = "  Residuos\t Sólidos  ";
        var mapped = mapper.mapearProyecto(project(1L, raw, "01/01/2017", "Otros"));
        assertThat(mapped.actividad()).isEqualTo(raw);
        assertThat(mapped.claveActividad()).isEqualTo("RESIDUOS SOLIDOS");
        assertThat(mapper.normalizarActividad("Mineri\u0301a")).isEqualTo("MINERIA");
        assertThat(mapper.normalizarActividad("\u00a0Energía\u00a0")).isEqualTo("ENERGIA");
        assertThat(mapper.normalizarActividad("Transportes")).isEqualTo("TRANSPORTES");
        assertThat(mapper.normalizarActividad("Hidrocarburos")).isEqualTo("HIDROCARBUROS");
    }

    @Test
    void selectionCountsRecordsAndApprovedRowsAndUses2017ByDefault() {
        List<ProyectoSenaceDto> rows = List.of(
                project(1L, "Minería", "05/04/2017", "Aprobado"),
                project(2L, "MINERIA", "06/04/2017", "Otros"),
                project(3L, "Minería", "05/04/2018", "Aprobado"),
                project(4L, "Pesca", "05/04/2017", "Aprobado"));
        var result = mapper.mapearProyectos(rows, "  minería ", null);
        assertThat(result.registros()).extracting(SenaceMapper.ProyectoMapeado::id).containsExactly(1L, 2L);
        assertThat(result.conteos()).isEqualTo(new Conteos(4, 4, 2, 1, 0, 0));
        assertThat(result.estadoFiltrado()).isEqualTo(EstadoFiltrado.APLICADO);
        assertThat(result.origen()).isEqualTo(Origen.API_DIRECTA_SENACE);
        assertThat(result.cobertura()).isEqualTo(Cobertura.NO_VERIFICADA);
        assertThat(result.cuerpoCompleto()).isNull(); // El mapper no vio un cuerpo HTTP.
        assertThat(mapper.mapearProyectos(rows, "TODOS", 2018).registros())
                .extracting(SenaceMapper.ProyectoMapeado::id).containsExactly(3L);
    }

    @Test
    void invalidTemporalRowsRemainVisibleInCountsWithoutChangingTheirYear() {
        var result = mapper.mapearProyectos(List.of(
                project(1L, "Minería", "01/01/2017", "Aprobado"),
                project(2L, "Minería", "31/02/2017", "Aprobado"),
                project(3L, "Pesca", null, "Otros")), "Mineria", 2017);
        assertThat(result.registros()).hasSize(1);
        assertThat(result.conteos()).isEqualTo(new Conteos(3, 3, 1, 1, 2, 1));
        assertThat(result.estadoConsulta()).isEqualTo(EstadoConsulta.EXITO);
        assertThat(result.estadoFiltrado()).isEqualTo(EstadoFiltrado.NO_EVALUABLE);
        assertThat(result.motivo()).isEqualTo(Motivo.FECHAS_NO_EVALUABLES);
        assertThat(result.advertencias()).contains(Advertencia.FECHA_INVALIDA, Advertencia.FECHA_AUSENTE);
    }

    @Test
    void officialEmptySelectionEmptyAndErrorAreDifferentWithIndependentMetadata() {
        var empty = mapper.mapearProyectos(List.of(), "TODOS", 2017);
        var unmatched = mapper.mapearProyectos(List.of(project(1L, "Pesca", "01/01/2018", "Otros")), "TODOS", 2017);
        var invalid = mapper.mapearProyectos(null, "TODOS", 2017);
        var external = ResultadoConsultaSenace.errorExterno();
        var partial = ResultadoConsultaSenace.parcial();
        assertThat(empty.registros()).isEmpty();
        assertThat(empty.origen()).isEqualTo(Origen.API_DIRECTA_SENACE);
        assertThat(empty.estadoConsulta()).isEqualTo(EstadoConsulta.VACIA);
        assertThat(empty.conteos().recibidos()).isZero();
        assertThat(unmatched.estadoConsulta()).isEqualTo(EstadoConsulta.EXITO);
        assertThat(unmatched.estadoFiltrado()).isEqualTo(EstadoFiltrado.SIN_COINCIDENCIAS);
        assertThat(unmatched.conteos().recibidos()).isEqualTo(1);
        assertThat(invalid.estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
        assertThat(invalid.conteos().recibidos()).isNull();
        assertThat(external.estadoConsulta()).isEqualTo(EstadoConsulta.ERROR_EXTERNO);
        assertThat(external.origen()).isEqualTo(Origen.NO_DISPONIBLE);
        assertThat(external.conteos().aprobados()).isNull();
        assertThat(partial.estadoConsulta()).isEqualTo(EstadoConsulta.PARCIAL);
        assertThat(partial.cuerpoCompleto()).isFalse();
        assertThat(partial.registros()).isEmpty();
        assertThat(partial.conteos().recibidos()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"-90,-180", "90,180", "0,0", "-12.123456789012345678901,-75.123456789012345678901"})
    void validCoordinatesRetainPrecisionAndActualZeroIsNotInvented(String latitude, String longitude) {
        ProyectoSenaceDto dto = withCoordinates(new BigDecimal(latitude), new BigDecimal(longitude));
        var mapped = mapper.mapearProyecto(dto);
        assertThat(mapped.latitud()).isEqualTo(dto.latitud());
        assertThat(mapped.longitud()).isEqualTo(dto.longitud());
        assertThat(mapped.advertencias()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"90.0001,0", "-90.0001,0", "0,180.0001", "0,-180.0001"})
    void outOfRangeCoordinatePairIsAbsentWithWarningAndOriginalPreserved(String latitude, String longitude) {
        ProyectoSenaceDto dto = withCoordinates(new BigDecimal(latitude), new BigDecimal(longitude));
        var mapped = mapper.mapearProyecto(dto);
        assertThat(mapped.latitud()).isNull();
        assertThat(mapped.longitud()).isNull();
        assertThat(mapped.original().latitud()).isEqualTo(dto.latitud());
        assertThat(mapped.advertencias()).containsExactly(Advertencia.COORDENADAS_FUERA_DE_RANGO);
    }

    @Test
    void missingAndIncompleteCoordinatesNeverBecomeZero() {
        var absent = mapper.mapearProyecto(withCoordinates(null, null));
        var incomplete = mapper.mapearProyecto(withCoordinates(new BigDecimal("-12"), null));
        assertThat(absent.latitud()).isNull();
        assertThat(absent.longitud()).isNull();
        assertThat(absent.advertencias()).isEmpty();
        assertThat(incomplete.latitud()).isNull();
        assertThat(incomplete.longitud()).isNull();
        assertThat(incomplete.advertencias()).containsExactly(Advertencia.COORDENADAS_INCOMPLETAS);
    }

    @Test
    void consultantsMapEightOfficialFieldsWithoutDeducingIdentityOrValidity() throws Exception {
        String json = """
                {"RAZON_SOCIAL":"CONSULTORA ARTIFICIAL DE PRUEBA","ACTIVIDAD":"  Minería  ",
                 "SUBSECTOR":"Subsector de prueba","TIPO_SOLICITUD":"Solicitud de prueba",
                 "RESOLUCION":" Resolucion de prueba ","FECHA_RD":"fecha no perfilada",
                 "INICIO_VIGENCIA_RD":"texto original","FIN_VIGENCIA_RD":""}
                """;
        ConsultoraSenaceDto dto = jackson.readValue(json, ConsultoraSenaceDto.class);
        var mapped = mapper.mapearConsultora(dto);
        assertThat(jackson.valueToTree(dto).size()).isEqualTo(8);
        assertThat(mapped.original()).isSameAs(dto);
        assertThat(mapped.razonSocial()).isEqualTo(dto.razonSocial());
        assertThat(mapped.actividad()).isEqualTo("  Minería  ");
        assertThat(mapped.claveActividad()).isEqualTo("MINERIA");
        assertThat(mapped.subsector()).isEqualTo("Subsector de prueba");
        assertThat(mapped.tipoSolicitud()).isEqualTo(dto.tipoSolicitud());
        assertThat(mapped.resolucion()).isEqualTo(" Resolucion de prueba ");
        assertThat(mapped.fechaRd()).isEqualTo("fecha no perfilada");
        assertThat(mapped.inicioVigenciaRd()).isEqualTo("texto original");
        assertThat(mapped.finVigenciaRd()).isNull();
        assertThat(mapped.original().finVigenciaRd()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void consultantsHandleOptionalBlanksWithoutChangingOriginalDto(String blank) {
        ConsultoraSenaceDto dto = new ConsultoraSenaceDto(blank, blank, blank, blank, blank, blank, blank, blank);
        var mapped = mapper.mapearConsultora(dto);
        assertThat(mapped.original().fechaRd()).isEqualTo(blank);
        assertThat(mapped.actividad()).isEqualTo(blank);
        assertThat(mapped.razonSocial()).isNull();
        assertThat(mapped.subsector()).isNull();
        assertThat(mapped.resolucion()).isNull();
        assertThat(mapped.inicioVigenciaRd()).isNull();
        assertThat(mapped.finVigenciaRd()).isNull();
    }

    @Test
    void repeatedConsultantRowsAreRetainedInOriginalOrder() {
        ConsultoraSenaceDto dto = consultant();
        var result = mapper.mapearConsultoras(List.of(dto, dto));
        assertThat(result.registros()).hasSize(2);
        assertThat(result.registros().get(0)).isEqualTo(result.registros().get(1));
        assertThat(result.conteos()).isEqualTo(new Conteos(2, 2, 2, null, null, null));
        assertThat(result.estadoFiltrado()).isEqualTo(EstadoFiltrado.NO_APLICABLE);
        assertThat(result.origen()).isEqualTo(Origen.API_DIRECTA_SENACE);
        assertThat(result.cuerpoCompleto()).isNull();
    }

    @Test
    void consultantsHaveOfficialEmptyMetadataAndRejectNullRowsWithoutPublishingPrefix() {
        var empty = mapper.mapearConsultoras(List.of());
        assertThat(empty.estadoConsulta()).isEqualTo(EstadoConsulta.VACIA);
        assertThat(empty.origen()).isEqualTo(Origen.API_DIRECTA_SENACE);
        assertThat(empty.conteos().devueltos()).isZero();
        assertThat(empty.conteos().aprobados()).isNull();
        var invalid = mapper.mapearConsultoras(Arrays.asList(consultant(), null));
        assertThat(invalid.estadoConsulta()).isEqualTo(EstadoConsulta.FORMATO_INVALIDO);
        assertThat(invalid.registros()).isEmpty();
        assertThat(invalid.conteos().recibidos()).isEqualTo(2);
        assertThat(mapper.mapearConsultoras(null).motivo()).isEqualTo(Motivo.LISTA_AUSENTE);
        assertThatThrownBy(() -> mapper.mapearConsultora(null)).hasMessage("FILA_NULA");
    }

    @Test
    void resultDefensivelyCopiesRowsAndSeparatesExplicitSimulation() {
        List<String> mutable = new ArrayList<>(List.of("FILA DE PRUEBA"));
        var result = new ResultadoConsultaSenace<>(mutable, Origen.SIMULADO, EstadoConsulta.EXITO,
                EstadoFiltrado.NO_APLICABLE, Cobertura.NO_VERIFICADA, null, null,
                new Conteos(1, 1, 1, null, null, null), List.of());
        mutable.clear();
        assertThat(result.registros()).containsExactly("FILA DE PRUEBA");
        assertThat(result.origen()).isEqualTo(Origen.SIMULADO);
        assertThatThrownBy(() -> result.registros().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultRejectsFalseCountsAndRowsPublishedAsErrors() {
        assertThatThrownBy(() -> new Conteos(-1, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Conteos(1, 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoConsultaSenace<>(List.of("FILA DE PRUEBA"),
                Origen.NO_DISPONIBLE, EstadoConsulta.ERROR_EXTERNO, EstadoFiltrado.NO_APLICABLE,
                Cobertura.NO_VERIFICADA, null, Motivo.ERROR_EXTERNO, Conteos.desconocidos(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProyectoSenaceDto project(Long id, String activity, String date, String state) {
        return new ProyectoSenaceDto(id, "ENTIDAD ARTIFICIAL DE PRUEBA", "00000000001",
                "Titulo artificial", "Unidad artificial", "TIPO_PRUEBA", activity, date,
                state, "Descripcion artificial", null, null, "Resolucion de prueba");
    }

    private ProyectoSenaceDto withCoordinates(BigDecimal latitude, BigDecimal longitude) {
        ProyectoSenaceDto base = project(1L, "Minería", "01/01/2017", "Aprobado");
        return new ProyectoSenaceDto(base.id(), base.titular(), base.ruc(), base.tituloProyecto(),
                base.unidadProyecto(), base.tipo(), base.actividad(), base.fechaInicio(),
                base.estado(), base.descripcion(), longitude, latitude, base.resolucion());
    }

    private ConsultoraSenaceDto consultant() {
        return new ConsultoraSenaceDto("CONSULTORA ARTIFICIAL DE PRUEBA", "Actividad de prueba",
                "Subsector de prueba", "Solicitud de prueba", "Resolucion de prueba",
                "fecha no interpretada", "inicio no interpretado", "fin no interpretado");
    }
}
