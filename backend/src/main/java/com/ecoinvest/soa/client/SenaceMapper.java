package com.ecoinvest.soa.client;

import com.ecoinvest.soa.client.ResultadoConsultaSenace.Advertencia;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Cobertura;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Conteos;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoConsulta;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.EstadoFiltrado;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Motivo;
import com.ecoinvest.soa.client.ResultadoConsultaSenace.Origen;
import com.ecoinvest.soa.client.dto.ConsultoraSenaceDto;
import com.ecoinvest.soa.client.dto.ProyectoSenaceDto;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Mapeo puro de filas ya deserializadas. No acredita transporte, cobertura ni unicidad global. */
public final class SenaceMapper {
    public static final int ANIO_PREDETERMINADO = 2017;
    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("dd/MM/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal LIMITE_LATITUD = new BigDecimal("90");
    private static final BigDecimal LIMITE_LONGITUD = new BigDecimal("180");

    public enum EstadoFecha { VALIDA, AUSENTE, INVALIDA }

    /** original preserva todos los valores externos; las propiedades opcionales normalizadas son separadas. */
    public record ProyectoMapeado(
            ProyectoSenaceDto original,
            Long id, String titular, String ruc, String tituloProyecto, String unidadProyecto,
            String tipo, String actividad, String claveActividad, String fechaInicio,
            LocalDate fechaInicioNormalizada, Integer anioInicio, EstadoFecha estadoFecha,
            String estado, String descripcion, BigDecimal longitud, BigDecimal latitud,
            String resolucion, List<Advertencia> advertencias) {
        public ProyectoMapeado {
            advertencias = List.copyOf(advertencias);
        }
    }

    /** No tiene RUC, registro, nivel, estado, ID o vigencia derivados. */
    public record ConsultoraMapeada(
            ConsultoraSenaceDto original,
            String razonSocial, String actividad, String claveActividad, String subsector,
            String tipoSolicitud, String resolucion, String fechaRd,
            String inicioVigenciaRd, String finVigenciaRd) {
    }

    private record FechaInterpretada(LocalDate fecha, EstadoFecha estado) {
    }

    public ProyectoMapeado mapearProyecto(ProyectoSenaceDto origen) {
        if (origen == null) throw new IllegalArgumentException("FILA_NULA");
        if (origen.id() == null) throw new IllegalArgumentException("ID_AUSENTE");
        // No se impone positividad o estabilidad no acreditada del identificador.
        List<Advertencia> advertencias = new ArrayList<>();
        FechaInterpretada fecha = interpretarFecha(origen.fechaInicio());
        if (fecha.estado() == EstadoFecha.AUSENTE) advertencias.add(Advertencia.FECHA_AUSENTE);
        if (fecha.estado() == EstadoFecha.INVALIDA) advertencias.add(Advertencia.FECHA_INVALIDA);

        BigDecimal latitud = origen.latitud();
        BigDecimal longitud = origen.longitud();
        if ((latitud == null) != (longitud == null)) {
            advertencias.add(Advertencia.COORDENADAS_INCOMPLETAS);
            latitud = null;
            longitud = null;
        } else if (latitud != null && (latitud.abs().compareTo(LIMITE_LATITUD) > 0
                || longitud.abs().compareTo(LIMITE_LONGITUD) > 0)) {
            advertencias.add(Advertencia.COORDENADAS_FUERA_DE_RANGO);
            latitud = null;
            longitud = null;
        }
        // Rangos bajo supuesto de grados: no valida CRS, region ni ubicacion real.
        return new ProyectoMapeado(origen, origen.id(), opcional(origen.titular()),
                opcional(origen.ruc()), opcional(origen.tituloProyecto()), opcional(origen.unidadProyecto()),
                opcional(origen.tipo()), origen.actividad(), normalizarActividad(origen.actividad()),
                origen.fechaInicio(), fecha.fecha(), fecha.fecha() == null ? null : fecha.fecha().getYear(),
                fecha.estado(), opcional(origen.estado()), opcional(origen.descripcion()),
                longitud, latitud, opcional(origen.resolucion()), advertencias);
    }

    public ConsultoraMapeada mapearConsultora(ConsultoraSenaceDto origen) {
        if (origen == null) throw new IllegalArgumentException("FILA_NULA");
        // No se presupone una clave o que una razon social vacia invalide toda una empresa.
        return new ConsultoraMapeada(origen, opcional(origen.razonSocial()), origen.actividad(),
                normalizarActividad(origen.actividad()), opcional(origen.subsector()),
                opcional(origen.tipoSolicitud()), opcional(origen.resolucion()),
                opcional(origen.fechaRd()), opcional(origen.inicioVigenciaRd()),
                opcional(origen.finVigenciaRd()));
    }

    /**
     * anio nulo conserva el default 2017; actividad nula/blanca o TODOS no limita actividad.
     * El origen oficial es una declaracion del flujo esperado: este metodo no realiza HTTP.
     */
    public ResultadoConsultaSenace<ProyectoMapeado> mapearProyectos(
            List<ProyectoSenaceDto> registros, String actividad, Integer anio) {
        if (registros == null) return ResultadoConsultaSenace.formatoInvalido(Motivo.LISTA_AUSENTE, null);
        for (ProyectoSenaceDto fila : registros) {
            if (fila == null) return ResultadoConsultaSenace.formatoInvalido(Motivo.FILA_NULA, registros.size());
            if (fila.id() == null) return ResultadoConsultaSenace.formatoInvalido(Motivo.ID_AUSENTE, registros.size());
        }
        if (registros.isEmpty()) {
            return new ResultadoConsultaSenace<>(List.of(), Origen.API_DIRECTA_SENACE,
                    EstadoConsulta.VACIA, EstadoFiltrado.NO_APLICABLE, Cobertura.NO_VERIFICADA,
                    null, Motivo.RESPUESTA_OFICIAL_VACIA, new Conteos(0, 0, 0, 0, 0, 0), List.of());
        }

        int anioFiltro = anio != null ? anio : ANIO_PREDETERMINADO;
        String claveFiltro = normalizarActividad(actividad);
        List<ProyectoMapeado> seleccionados = new ArrayList<>();
        Set<Advertencia> advertencias = new LinkedHashSet<>();
        int fechasNoEvaluables = 0;
        int fechasNoEvaluablesEnActividad = 0;
        int aprobados = 0;
        for (ProyectoSenaceDto fila : registros) {
            ProyectoMapeado proyecto = mapearProyecto(fila);
            advertencias.addAll(proyecto.advertencias());
            boolean coincideActividad = claveFiltro == null || "TODOS".equals(claveFiltro)
                    || claveFiltro.equals(proyecto.claveActividad());
            if (proyecto.estadoFecha() != EstadoFecha.VALIDA) {
                fechasNoEvaluables++;
                if (coincideActividad) fechasNoEvaluablesEnActividad++;
                continue;
            }
            if (coincideActividad && proyecto.anioInicio() == anioFiltro) {
                seleccionados.add(proyecto);
                if ("Aprobado".equalsIgnoreCase(proyecto.estado())) aprobados++;
            }
        }
        EstadoFiltrado estadoFiltrado = fechasNoEvaluablesEnActividad > 0 ? EstadoFiltrado.NO_EVALUABLE
                : seleccionados.isEmpty() ? EstadoFiltrado.SIN_COINCIDENCIAS : EstadoFiltrado.APLICADO;
        Motivo motivo = fechasNoEvaluablesEnActividad > 0 ? Motivo.FECHAS_NO_EVALUABLES
                : seleccionados.isEmpty() ? Motivo.SIN_COINCIDENCIAS : null;
        // Conteos exactos de la lista evaluada/devuelta, no de proyectos unicos o universo oficial.
        return new ResultadoConsultaSenace<>(seleccionados, Origen.API_DIRECTA_SENACE,
                EstadoConsulta.EXITO, estadoFiltrado, Cobertura.NO_VERIFICADA, null, motivo,
                new Conteos(registros.size(), registros.size(), seleccionados.size(), aprobados,
                        fechasNoEvaluables, fechasNoEvaluablesEnActividad), new ArrayList<>(advertencias));
    }

    public ResultadoConsultaSenace<ConsultoraMapeada> mapearConsultoras(List<ConsultoraSenaceDto> registros) {
        if (registros == null) return ResultadoConsultaSenace.formatoInvalido(Motivo.LISTA_AUSENTE, null);
        for (ConsultoraSenaceDto fila : registros) {
            if (fila == null) return ResultadoConsultaSenace.formatoInvalido(Motivo.FILA_NULA, registros.size());
        }
        List<ConsultoraMapeada> mapeados = new ArrayList<>(registros.size());
        for (ConsultoraSenaceDto fila : registros) mapeados.add(mapearConsultora(fila));
        return new ResultadoConsultaSenace<>(mapeados, Origen.API_DIRECTA_SENACE,
                registros.isEmpty() ? EstadoConsulta.VACIA : EstadoConsulta.EXITO,
                EstadoFiltrado.NO_APLICABLE, Cobertura.NO_VERIFICADA, null,
                registros.isEmpty() ? Motivo.RESPUESTA_OFICIAL_VACIA : null,
                new Conteos(registros.size(), registros.size(), mapeados.size(), null, null, null), List.of());
    }

    public String normalizarActividad(String actividad) {
        if (actividad == null) return null;
        String espacios = actividad.replaceAll("[\\p{Z}\\s]+", " ").trim();
        if (espacios.isEmpty()) return null;
        return Normalizer.normalize(espacios, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    private FechaInterpretada interpretarFecha(String original) {
        if (original == null || original.isBlank()) return new FechaInterpretada(null, EstadoFecha.AUSENTE);
        if (!original.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) return new FechaInterpretada(null, EstadoFecha.INVALIDA);
        try {
            return new FechaInterpretada(LocalDate.parse(original, FECHA), EstadoFecha.VALIDA);
        } catch (DateTimeParseException exception) {
            // No propagar mensaje con el texto de fecha recibido.
            return new FechaInterpretada(null, EstadoFecha.INVALIDA);
        }
    }

    private static String opcional(String value) {
        // Un no-vacio mantiene el valor exacto: no recortar titulos, resoluciones o nombres.
        return value == null || value.isBlank() ? null : value;
    }
}
