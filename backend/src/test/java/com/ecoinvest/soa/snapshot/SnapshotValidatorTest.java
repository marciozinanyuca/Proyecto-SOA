package com.ecoinvest.soa.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Todos los objetos son artificiales; no se incluyen filas de proveedores. */
class SnapshotValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SnapshotValidator validator = new SnapshotValidator();

    @Test void springConfigurationIsDisabledByDefaultAndDoesNotCreateRepository() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(SnapshotConfiguration.class);
            context.refresh();
            assertTrue(context.getBeansOfType(SnapshotRepository.class).isEmpty());
        }
    }

    @Test void agePolicyMustBeExplicitAndOrdered() {
        var directory = java.nio.file.Path.of("not-created-by-constructor");
        assertThrows(IllegalArgumentException.class, () -> new SnapshotProperties(directory, 16384,
                null, java.time.Duration.ofDays(1), java.time.Duration.ZERO, java.time.Duration.ofDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotProperties(directory, 16384,
                java.time.Duration.ofDays(2), java.time.Duration.ofDays(1),
                java.time.Duration.ZERO, java.time.Duration.ofDays(1)));
    }

    @Test void keysRejectPathsUnknownResourcesAndWrongPeriods() {
        assertThrows(IllegalArgumentException.class, () -> SnapshotKey.bcrp("../x", 2017));
        assertThrows(IllegalArgumentException.class, () -> SnapshotKey.bcrp("PM04973AA", 0));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotKey(SnapshotKey.Source.SENACE, "Visitas", null));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotKey(SnapshotKey.Source.SENACE, "CarteraProyectos", 2017));
    }

    @Test void projectsPreservePrecisionTextAndOptionalQualityProblems() throws Exception {
        JsonNode payload = mapper.readTree("""
                [{"ID":9007199254740993,"RUC":"00000000001","FECHA_INICIO":"fecha artificial",
                  "LONGITUD":-77.1234567890123456789,"LATITUD":91}]
                """);
        assertTrue(validator.validPayload(SnapshotKey.proyectos(), payload));
        assertFalse(validator.validPayload(SnapshotKey.proyectos(), mapper.readTree("[{\"ID\":1.5}]")));
        assertFalse(validator.validPayload(SnapshotKey.proyectos(), mapper.readTree("[{\"ID\":9223372036854775808}]")));
        assertFalse(validator.validPayload(SnapshotKey.proyectos(), mapper.readTree("[{}]")));
        assertFalse(validator.validPayload(SnapshotKey.proyectos(), mapper.readTree("[{\"ID\":1,\"RUC\":123}]")));
    }

    @Test void consultantDuplicatesAndEmptyFieldsAreAllowedButExtraMetadataIsNot() throws Exception {
        assertTrue(validator.validPayload(SnapshotKey.consultoras(), mapper.readTree(
                "[{\"RAZON_SOCIAL\":\"PRUEBA\",\"FECHA_RD\":\"\"},{\"RAZON_SOCIAL\":\"PRUEBA\"}]")));
        assertTrue(validator.validPayload(SnapshotKey.consultoras(), mapper.readTree("[]")));
        assertFalse(validator.validPayload(SnapshotKey.consultoras(), mapper.readTree("[null]")));
        assertFalse(validator.validPayload(SnapshotKey.consultoras(), mapper.readTree("[{\"auth_key\":\"artificial\"}]")));
        assertFalse(validator.validPayload(SnapshotKey.consultoras(), mapper.readTree("{}")));
    }

    @Test void economicPayloadMustMatchExactKeyAndContainNumericValueOnly() throws Exception {
        var key = SnapshotKey.bcrp("PM04973AA", 2017);
        JsonNode payload = mapper.readTree("{\"serie\":\"PM04973AA\",\"periodo\":\"2017\",\"valor\":4.1234567890123456789}");
        assertTrue(validator.validPayload(key, payload));
        assertFalse(validator.validPayload(SnapshotKey.bcrp("PM04973AA", 2018), payload));
        assertFalse(validator.validPayload(SnapshotKey.bcrp("PM04974AA", 2017), payload));
        assertFalse(validator.validPayload(key, mapper.readTree("{\"serie\":\"PM04973AA\",\"periodo\":\"2017\",\"valor\":null}")));
    }

    @Test void envelopeChecksSchemaIntegrityTimestampsCompletenessAndIdentity() throws Exception {
        Instant now = Instant.parse("2026-11-11T12:00:00Z");
        var original = envelope(1, now, true, mapper.readTree("[{\"ID\":1}]"), null);
        var valid = envelope(1, now, true, original.payload(), validator.checksum(original));
        assertTrue(validator.valid(valid, SnapshotKey.proyectos(), now));
        assertFalse(validator.valid(valid, SnapshotKey.consultoras(), now));
        assertFalse(validator.valid(valid, SnapshotKey.proyectos(), now.minusSeconds(1)));
        assertFalse(validator.valid(envelope(2, now, true, valid.payload(), valid.checksum()), SnapshotKey.proyectos(), now));
        assertFalse(validator.valid(envelope(1, now, false, valid.payload(), valid.checksum()), SnapshotKey.proyectos(), now));
        assertFalse(validator.valid(envelope(1, now, true, mapper.readTree("[{\"ID\":2}]"), valid.checksum()), SnapshotKey.proyectos(), now));
    }

    @Test void defensiveCopyAndSafeToStringDoNotExposePayload() throws Exception {
        Instant now = Instant.parse("2026-11-11T12:00:00Z");
        var source = mapper.createArrayNode().addObject().put("ID", 1).put("TITULAR", "PRUEBA_PRIVADA");
        var envelope = envelope(1, now, true, mapper.createArrayNode().add(source), null);
        source.put("ID", 2);
        assertEquals(1, envelope.payload().get(0).path("ID").intValue());
        ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.payload().get(0)).put("ID", 3);
        assertEquals(1, envelope.payload().get(0).path("ID").intValue());
        assertFalse(envelope.toString().contains("PRUEBA_PRIVADA"));
    }

    private SnapshotEnvelope envelope(int version, Instant date, boolean full, JsonNode payload, String checksum) {
        return new SnapshotEnvelope(version, SnapshotKey.proyectos(), date.minusSeconds(1), date,
                full, "NO_VERIFICADA", "VALIDADO", payload.size(), payload, checksum);
    }
}
