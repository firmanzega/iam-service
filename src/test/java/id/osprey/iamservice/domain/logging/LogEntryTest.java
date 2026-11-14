package id.osprey.iamservice.domain.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji entri log terstruktur: salinan atribut harus defensif karena
 * pemetaan log sering dibaca setelah pemanggil keluar lingkup.
 */
class LogEntryTest {

    @Test
    void constructor_defensiveCopiesAttributes() {
        Map<String, Object> attributes = new java.util.HashMap<>(Map.of("key", "value"));

        LogEntry entry = new LogEntry(LogCategory.ERRORS, "gagal", 7L, "trace-1",
                Instant.parse("2026-01-01T00:00:00Z"), attributes);

        attributes.clear();

        assertEquals(Map.of("key", "value"), entry.getAttributes(),
                "attributes harus disalin, bukan direferensikan");
        assertNotSame(attributes, entry.getAttributes());
    }

    @Test
    void constructor_nullAttributes_becomesEmptyMap() {
        LogEntry entry = new LogEntry(LogCategory.GENERALS, "pesan", null, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);

        assertTrue(entry.getAttributes().isEmpty());
    }

    @Test
    void accessors_returnConstructorValues() {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        LogEntry entry = new LogEntry(LogCategory.INTEGRATIONS, "pesan integrasi", 3L,
                "trace-9", timestamp, Map.of("a", 1));

        assertEquals(LogCategory.INTEGRATIONS, entry.getCategory());
        assertEquals("pesan integrasi", entry.getMessage());
        assertEquals(3L, entry.getTenantId());
        assertEquals("trace-9", entry.getTraceId());
        assertEquals(timestamp, entry.getTimestamp());
    }

    @Test
    void logCategory_roundTripsAllConstants() {
        for (LogCategory category : LogCategory.values()) {
            assertEquals(category, LogCategory.valueOf(category.name()));
        }
        assertEquals(5, LogCategory.values().length);
    }

    @Test
    void logCategory_unknownName_throws() {
        assertThrows(IllegalArgumentException.class, () -> LogCategory.valueOf("DEBUGRRR"));
    }
}
