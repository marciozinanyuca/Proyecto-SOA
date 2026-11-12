package com.ecoinvest.soa.snapshot;

import java.nio.file.Path;
import java.util.Set;

/** Identificadores cerrados: ninguna entrada del usuario se interpreta como ruta. */
public record SnapshotKey(Source source, String resource, Integer year) {
    public enum Source { SENACE, BCRP }
    private static final Set<String> SERIES = Set.of("PM04969AA", "PM04971AA", "PM04973AA",
            "PM04974AA", "PM04978AA", "PM04983AA");

    public SnapshotKey {
        if (source == null || resource == null) throw new IllegalArgumentException("CLAVE_INVALIDA");
        if (source == Source.SENACE) {
            if (year != null || !(resource.equals("CarteraProyectos")
                    || resource.equals("ConsultorasAmbientales"))) {
                throw new IllegalArgumentException("RECURSO_NO_AUTORIZADO");
            }
        } else if (!SERIES.contains(resource) || year == null || year < 1 || year > 9999) {
            throw new IllegalArgumentException("SERIE_PERIODO_NO_AUTORIZADO");
        }
    }

    public static SnapshotKey proyectos() { return new SnapshotKey(Source.SENACE, "CarteraProyectos", null); }
    public static SnapshotKey consultoras() { return new SnapshotKey(Source.SENACE, "ConsultorasAmbientales", null); }
    public static SnapshotKey bcrp(String series, int year) { return new SnapshotKey(Source.BCRP, series, year); }

    Path relativePath() {
        if (source == Source.BCRP) return Path.of("bcrp", resource, String.format(java.util.Locale.ROOT, "%04d.json", year));
        return Path.of("senace", resource.equals("CarteraProyectos")
                ? "cartera-proyectos.json" : "consultoras-ambientales.json");
    }
}
