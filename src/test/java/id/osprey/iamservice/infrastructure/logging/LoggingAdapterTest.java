package id.osprey.iamservice.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import id.osprey.iamservice.domain.logging.LogCategory;
import id.osprey.iamservice.domain.logging.LogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji adapter SLF4J: tingkat log per kategori, dan disiplin MDC.
 *
 * MDC yang tidak dibersihkan adalah bug klasik: satu permintaan yang lupa
 * menghapus tenantId membuat permintaan berikutnya di thread yang sama
 * memakai tenant yang salah. Pengujian memeriksa nilai MDC yang terekam
 * pada event log dan nilai MDC sesudahnya.
 */
class LoggingAdapterTest {

    private final LoggingAdapter adapter = new LoggingAdapter();
    private ListAppender<ILoggingEvent> appender;
    private Logger adapterLogger;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        adapterLogger = (Logger) LoggerFactory.getLogger(LoggingAdapter.class);
        adapterLogger.addAppender(appender);
    }

    @AfterEach
    void clearMdcAndDetach() {
        MDC.remove("tenantId");
        MDC.remove("traceId");
        adapterLogger.detachAppender(appender);
    }

    @Test
    void errorCategory_logsAtErrorLevel_withTenantAndTraceInMdc() {
        adapter.write(entry(LogCategory.ERRORS, "gagal", 7L, "trace-1"));

        ILoggingEvent event = singleEvent();

        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("gagal", event.getFormattedMessage());
        assertEquals("7", event.getMDCPropertyMap().get("tenantId"));
        assertEquals("trace-1", event.getMDCPropertyMap().get("traceId"));
    }

    @Test
    void nonErrorCategories_logAtInfoLevel() {
        adapter.write(entry(LogCategory.GENERALS, "umum", null, null));
        adapter.write(entry(LogCategory.INTEGRATIONS, "integrasi", null, null));
        adapter.write(entry(LogCategory.TIMERS, "durasi", null, null));
        adapter.write(entry(LogCategory.EMAILS, "surat", null, null));

        assertEquals(4, appender.list.size());
        assertTrue(appender.list.stream().allMatch(event -> event.getLevel() == Level.INFO));
    }

    @Test
    void write_withoutTenantId_doesNotLeakPreviousMdcIntoEvent() {
        MDC.put("tenantId", "sebelumnya");

        adapter.write(entry(LogCategory.GENERALS, "tanpa tenant", null, null));

        ILoggingEvent event = singleEvent();

        assertFalse(event.getMDCPropertyMap().containsKey("tenantId"),
                "entri tanpa tenantId tidak boleh mewarisi tenant MDC sebelumnya");
        assertEquals("sebelumnya", MDC.get("tenantId"),
                "nilai sebelumnya harus dipulihkan setelah penulisan");
    }

    @Test
    void write_restoresPreviousMdcAfterwards() {
        MDC.put("tenantId", "sebelumnya");
        MDC.put("traceId", "jejak-lama");

        adapter.write(entry(LogCategory.GENERALS, "sementara", 42L, "jejak-baru"));

        assertEquals("sebelumnya", MDC.get("tenantId"));
        assertEquals("jejak-lama", MDC.get("traceId"));
    }

    @Test
    void write_clearsMdc_whenThereWasNoPreviousValue() {
        adapter.write(entry(LogCategory.GENERALS, "sementara", 42L, "jejak-baru"));

        assertNull(MDC.get("tenantId"));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void write_withoutTraceId_doesNotLeakPreviousTraceIntoEvent() {
        MDC.put("traceId", "jejak-lama");

        adapter.write(entry(LogCategory.GENERALS, "tanpa jejak", 5L, null));

        ILoggingEvent event = singleEvent();

        assertFalse(event.getMDCPropertyMap().containsKey("traceId"));
        assertEquals("jejak-lama", MDC.get("traceId"));
    }

    private LogEntry entry(LogCategory category, String message, Long tenantId, String traceId) {
        return new LogEntry(category, message, tenantId, traceId, Instant.now(), Map.of());
    }

    private ILoggingEvent singleEvent() {
        assertEquals(1, appender.list.size(), "tepat satu baris log harus tertulis");
        return appender.list.get(0);
    }
}
