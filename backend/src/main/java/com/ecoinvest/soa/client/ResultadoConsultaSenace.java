package com.ecoinvest.soa.client;

import java.util.List;
import java.util.Objects;

/**
 * Resultado interno, no contrato HTTP. Un origen declarado por un mapper no acredita
 * transporte real. cuerpoCompleto permanece desconocido hasta que lo informe el cliente.
 */
public record ResultadoConsultaSenace<T>(
        List<T> registros,
        Origen origen,
        EstadoConsulta estadoConsulta,
        EstadoFiltrado estadoFiltrado,
        Cobertura cobertura,
        Boolean cuerpoCompleto,
        Motivo motivo,
        Conteos conteos,
        List<Advertencia> advertencias) {

    public enum Origen { API_DIRECTA_SENACE, NO_DISPONIBLE, SIMULADO }
    public enum EstadoConsulta { EXITO, VACIA, ERROR_EXTERNO, FORMATO_INVALIDO, PARCIAL }
    /** NO_EVALUABLE indica que al menos una fecha relevante no pudo evaluarse. */
    public enum EstadoFiltrado { APLICADO, SIN_COINCIDENCIAS, NO_EVALUABLE, NO_APLICABLE }
    public enum Cobertura { NO_VERIFICADA }
    public enum Motivo {
        RESPUESTA_OFICIAL_VACIA, SIN_COINCIDENCIAS, FECHAS_NO_EVALUABLES,
        LISTA_AUSENTE, FILA_NULA, ID_AUSENTE, ERROR_EXTERNO, FORMATO_INVALIDO,
        LIMITE_LECTURA, RESPUESTA_PARCIAL,
        CONFIGURACION_AUSENTE, CONFIGURACION_INVALIDA, CIRCUITO_ABIERTO,
        DESTINO_INVALIDO, RECURSO_INVALIDO, REDIRECCION_RECHAZADA,
        ERROR_HTTP, ERROR_TLS, TIMEOUT_CONEXION, TIMEOUT_INACTIVIDAD,
        PLAZO_TOTAL_AGOTADO, CODIFICACION_NO_ADMITIDA, INTERRUMPIDO
    }
    public enum Advertencia { FECHA_AUSENTE, FECHA_INVALIDA, COORDENADAS_INCOMPLETAS, COORDENADAS_FUERA_DE_RANGO }

    /** null es desconocido/no aplicable, nunca se sustituye por cero. */
    public record Conteos(
            Integer recibidos, Integer aceptados, Integer devueltos, Integer aprobados,
            Integer fechasNoEvaluables, Integer fechasNoEvaluablesEnActividad) {
        public Conteos {
            Integer[] values = {recibidos, aceptados, devueltos, aprobados,
                    fechasNoEvaluables, fechasNoEvaluablesEnActividad};
            for (Integer value : values) {
                if (value != null && value < 0) throw new IllegalArgumentException("CONTEO_NEGATIVO");
            }
            requireAtMost(aceptados, recibidos);
            requireAtMost(devueltos, aceptados);
            requireAtMost(aprobados, devueltos);
            requireAtMost(fechasNoEvaluables, aceptados);
            requireAtMost(fechasNoEvaluablesEnActividad, fechasNoEvaluables);
        }

        private static void requireAtMost(Integer value, Integer maximum) {
            if (value != null && maximum != null && value > maximum) {
                throw new IllegalArgumentException("CONTEOS_INCONSISTENTES");
            }
        }

        public static Conteos desconocidos() {
            return new Conteos(null, null, null, null, null, null);
        }
    }

    public ResultadoConsultaSenace {
        registros = List.copyOf(Objects.requireNonNull(registros));
        Objects.requireNonNull(origen);
        Objects.requireNonNull(estadoConsulta);
        Objects.requireNonNull(estadoFiltrado);
        Objects.requireNonNull(cobertura);
        Objects.requireNonNull(conteos);
        advertencias = List.copyOf(Objects.requireNonNull(advertencias));
        if (conteos.devueltos() != null && conteos.devueltos() != registros.size()) {
            throw new IllegalArgumentException("CONTEO_NO_COINCIDE_CON_LISTA");
        }
        if (estadoConsulta == EstadoConsulta.ERROR_EXTERNO
                || estadoConsulta == EstadoConsulta.FORMATO_INVALIDO
                || estadoConsulta == EstadoConsulta.PARCIAL) {
            if (!registros.isEmpty() || origen != Origen.NO_DISPONIBLE) {
                throw new IllegalArgumentException("ERROR_NO_PUEDE_PUBLICAR_FILAS_OFICIALES");
            }
        }
        if (estadoConsulta == EstadoConsulta.VACIA
                && (!registros.isEmpty() || origen != Origen.API_DIRECTA_SENACE
                    || !Integer.valueOf(0).equals(conteos.recibidos()))) {
            throw new IllegalArgumentException("RESPUESTA_VACIA_INCONSISTENTE");
        }
    }

    public static <T> ResultadoConsultaSenace<T> errorExterno() {
        return noDisponible(EstadoConsulta.ERROR_EXTERNO, Motivo.ERROR_EXTERNO, null, null);
    }

    public static <T> ResultadoConsultaSenace<T> formatoInvalido(Motivo motivo, Integer recibidos) {
        if (motivo != Motivo.LISTA_AUSENTE && motivo != Motivo.FILA_NULA
                && motivo != Motivo.ID_AUSENTE && motivo != Motivo.FORMATO_INVALIDO) {
            throw new IllegalArgumentException("MOTIVO_NO_ESTRUCTURAL");
        }
        return noDisponible(EstadoConsulta.FORMATO_INVALIDO, motivo, recibidos, null);
    }

    public static <T> ResultadoConsultaSenace<T> parcial() {
        return noDisponible(EstadoConsulta.PARCIAL, Motivo.RESPUESTA_PARCIAL, null, false);
    }

    private static <T> ResultadoConsultaSenace<T> noDisponible(
            EstadoConsulta estado, Motivo motivo, Integer recibidos, Boolean cuerpoCompleto) {
        return new ResultadoConsultaSenace<>(List.of(), Origen.NO_DISPONIBLE, estado,
                EstadoFiltrado.NO_APLICABLE, Cobertura.NO_VERIFICADA, cuerpoCompleto, motivo,
                new Conteos(recibidos, null, null, null, null, null), List.of());
    }
}
