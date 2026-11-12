package com.ecoinvest.soa.snapshot;

import com.ecoinvest.soa.client.SenaceMapper;
import com.ecoinvest.soa.client.dto.ConsultoraSenaceDto;
import com.ecoinvest.soa.client.dto.ProyectoSenaceDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/** Revalida tanto entradas nuevas como archivos locales. No certifica cobertura oficial. */
public final class SnapshotValidator {
    private static final Set<String> PROJECT_TEXT = Set.of("TITULAR", "RUC", "TITULO_PROYECTO",
            "UNIDAD_PROYECTO", "TIPO", "ACTIVIDAD", "FECHA_INICIO", "ESTADO", "DESCRIPCION", "RESOLUCION");
    private static final Set<String> CONSULTANT_TEXT = Set.of("RAZON_SOCIAL", "ACTIVIDAD", "SUBSECTOR",
            "TIPO_SOLICITUD", "RESOLUCION", "FECHA_RD", "INICIO_VIGENCIA_RD", "FIN_VIGENCIA_RD");
    private final ObjectMapper mapper = new ObjectMapper();
    private final SenaceMapper senaceMapper = new SenaceMapper();

    public boolean valid(SnapshotEnvelope envelope, SnapshotKey expected, Instant now) {
        try {
            return envelope != null && expected.equals(envelope.key())
                    && envelope.schemaVersion() == SnapshotEnvelope.SCHEMA_VERSION
                    && envelope.bodyComplete() && "NO_VERIFICADA".equals(envelope.coverage())
                    && "VALIDADO".equals(envelope.validation())
                    && envelope.capturedAt() != null && !envelope.capturedAt().isAfter(now)
                    && envelope.requestStartedAt() != null
                    && !envelope.requestStartedAt().isAfter(envelope.capturedAt())
                    && validPayload(expected, envelope.payload())
                    && envelope.recordCount() == count(expected, envelope.payload())
                    && checksum(envelope).equals(envelope.checksum());
        } catch (RuntimeException exception) { return false; }
    }

    public boolean validPayload(SnapshotKey key, JsonNode payload) {
        try {
            if (key == null || payload == null) return false;
            if (key.source() == SnapshotKey.Source.BCRP) {
                if (!payload.isObject() || payload.size() != 3
                        || !payload.path("serie").isTextual() || !key.resource().equals(payload.path("serie").textValue())
                        || !payload.path("periodo").isTextual()
                        || !String.format(java.util.Locale.ROOT, "%04d", key.year()).equals(payload.path("periodo").textValue())
                        || !payload.path("valor").isNumber()) return false;
                new BigDecimal(payload.get("valor").asText());
                return true;
            }
            if (!payload.isArray()) return false;
            boolean projects = key.equals(SnapshotKey.proyectos());
            Set<String> text = projects ? PROJECT_TEXT : CONSULTANT_TEXT;
            for (JsonNode row : payload) {
                if (!row.isObject()) return false;
                Iterator<String> fields = row.fieldNames();
                while (fields.hasNext()) {
                    String field = fields.next();
                    JsonNode value = row.get(field);
                    if (text.contains(field)) {
                        if (!value.isNull() && !value.isTextual()) return false;
                    } else if (projects && field.equals("ID")) {
                        if (!value.isIntegralNumber() || !value.canConvertToLong()) return false;
                    } else if (projects && (field.equals("LATITUD") || field.equals("LONGITUD"))) {
                        if (!value.isNull() && !value.isNumber()) return false;
                    } else return false; // No permite persistir campos ajenos, URL o secretos anexados.
                }
                if (projects) {
                    if (!row.hasNonNull("ID")) return false;
                    senaceMapper.mapearProyecto(mapper.treeToValue(row, ProyectoSenaceDto.class));
                } else senaceMapper.mapearConsultora(mapper.treeToValue(row, ConsultoraSenaceDto.class));
            }
            return true;
        } catch (Exception exception) { return false; }
    }

    int count(SnapshotKey key, JsonNode payload) { return key.source() == SnapshotKey.Source.SENACE ? payload.size() : 1; }

    String checksum(SnapshotEnvelope envelope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Metadatos y payload protegidos; canon numerico independiente de escala/orden JSON.
            update(digest, Integer.toString(envelope.schemaVersion()));
            update(digest, envelope.key().source().name());
            update(digest, envelope.key().resource());
            update(digest, String.valueOf(envelope.key().year()));
            update(digest, envelope.requestStartedAt().toString());
            update(digest, envelope.capturedAt().toString());
            update(digest, Boolean.toString(envelope.bodyComplete()));
            update(digest, envelope.coverage());
            update(digest, envelope.validation());
            update(digest, Integer.toString(envelope.recordCount()));
            hashNode(digest, envelope.payload());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA256_NO_DISPONIBLE"); }
    }

    private void hashNode(MessageDigest digest, JsonNode node) {
        if (node.isObject()) {
            update(digest, "object");
            Set<String> fields = new TreeSet<>();
            node.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) { update(digest, field); hashNode(digest, node.get(field)); }
            update(digest, "end");
        } else if (node.isArray()) {
            update(digest, "array");
            for (JsonNode child : node) hashNode(digest, child);
            update(digest, "end");
        } else if (node.isNumber()) {
            update(digest, "number");
            update(digest, node.decimalValue().stripTrailingZeros().toPlainString());
        } else { update(digest, node.getNodeType().name()); update(digest, node.asText()); }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
