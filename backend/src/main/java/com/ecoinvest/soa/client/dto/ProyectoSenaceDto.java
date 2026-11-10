package com.ecoinvest.soa.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.math.BigDecimal;

/** Transporte externo: textos originales y decimales, sin reglas HTTP o modelos generados. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProyectoSenaceDto(
        @JsonProperty("ID") @JsonDeserialize(using = IdEnteroDeserializer.class) Long id,
        @JsonProperty("TITULAR") String titular,
        @JsonProperty("RUC") String ruc,
        @JsonProperty("TITULO_PROYECTO") String tituloProyecto,
        @JsonProperty("UNIDAD_PROYECTO") String unidadProyecto,
        @JsonProperty("TIPO") String tipo,
        @JsonProperty("ACTIVIDAD") String actividad,
        @JsonProperty("FECHA_INICIO") String fechaInicio,
        @JsonProperty("ESTADO") String estado,
        @JsonProperty("DESCRIPCION") String descripcion,
        @JsonProperty("LONGITUD") BigDecimal longitud,
        @JsonProperty("LATITUD") BigDecimal latitud,
        @JsonProperty("RESOLUCION") String resolucion) {

    /** No permite que la coercion Jackson convierta 1.5 a 1 o desborde Long. */
    public static final class IdEnteroDeserializer extends StdDeserializer<Long> {
        public IdEnteroDeserializer() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Long.class, "ID requiere un entero JSON representable como Long");
            }
            try {
                return parser.getBigIntegerValue().longValueExact();
            } catch (ArithmeticException exception) {
                return context.reportInputMismatch(Long.class, "ID fuera del rango Long");
            }
        }
    }
}
