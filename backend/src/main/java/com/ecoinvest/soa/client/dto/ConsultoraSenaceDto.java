package com.ecoinvest.soa.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Una fila oficial recibida, no necesariamente una empresa unica ni un registro vigente. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultoraSenaceDto(
        @JsonProperty("RAZON_SOCIAL") String razonSocial,
        @JsonProperty("ACTIVIDAD") String actividad,
        @JsonProperty("SUBSECTOR") String subsector,
        @JsonProperty("TIPO_SOLICITUD") String tipoSolicitud,
        @JsonProperty("RESOLUCION") String resolucion,
        @JsonProperty("FECHA_RD") String fechaRd,
        @JsonProperty("INICIO_VIGENCIA_RD") String inicioVigenciaRd,
        @JsonProperty("FIN_VIGENCIA_RD") String finVigenciaRd) {
}
