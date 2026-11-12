package com.ecoinvest.soa.snapshot;

import com.ecoinvest.soa.client.ResultadoConsultaSenace;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Resultado exclusivamente interno: no es un modelo HTTP ni se registra automaticamente. */
public record ResultadoLkg<T>(T datos, Origen origen, IntentoApi intentoApi, Respaldo respaldo,
        CalidadSenace calidadSenace, SnapshotRepository.ReadStatus lectura,
        SnapshotRepository.WriteStatus escritura) {
    public enum Origen { API_DIRECTA_SENACE, API_DIRECTA_BCRP, RESPALDO_LOCAL, SIN_RESPALDO }
    public record IntentoApi(String estado, String motivo, Boolean cuerpoCompleto) { }
    public record Respaldo(Instant capturadoEn, Duration edad, boolean antiguo,
            int versionEsquema, String cobertura) { }
    public record CalidadSenace(ResultadoConsultaSenace.EstadoConsulta estadoDatos,
            ResultadoConsultaSenace.EstadoFiltrado filtrado, ResultadoConsultaSenace.Conteos conteos,
            List<ResultadoConsultaSenace.Advertencia> advertencias) {
        public CalidadSenace { advertencias = List.copyOf(advertencias); }
        public static CalidadSenace de(ResultadoConsultaSenace<?> mapped) {
            return new CalidadSenace(mapped.estadoConsulta(), mapped.estadoFiltrado(), mapped.conteos(), mapped.advertencias());
        }
    }

    public ResultadoLkg {
        Objects.requireNonNull(origen);
        Objects.requireNonNull(intentoApi);
        if (origen == Origen.RESPALDO_LOCAL && (respaldo == null
                || lectura != SnapshotRepository.ReadStatus.AVAILABLE || datos == null)) {
            throw new IllegalArgumentException("RESPALDO_NO_UTILIZABLE");
        }
        if (origen == Origen.SIN_RESPALDO && datos != null) throw new IllegalArgumentException("AUSENCIA_CON_DATOS");
    }

    public static Respaldo metadata(SnapshotRepository.ReadResult read) {
        return new Respaldo(read.snapshot().capturedAt(), read.age(), read.stale(),
                read.snapshot().schemaVersion(), read.snapshot().coverage());
    }

    @Override public String toString() { return "ResultadoLkg[origen=" + origen + ", contenido omitido]"; }
}
