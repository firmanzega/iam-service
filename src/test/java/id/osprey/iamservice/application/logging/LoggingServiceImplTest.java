package id.osprey.iamservice.application.logging;

import id.osprey.iamservice.domain.logging.LogEntry;
import id.osprey.iamservice.infrastructure.logging.LoggingAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Menguji penyusunan LogEntry dan pengalihan ke LoggingAdapter.
 *
 * Kategori diverifikasi satu per satu karena setiap pencatat
 * (error/general/integration/timer/email) punya jalur terpisah yang mudah
 * tertukar saat refactor.
 */
class LoggingServiceImplTest {

    private final LoggingAdapter adapter = mock(LoggingAdapter.class);
    private final LoggingServiceImpl service = new LoggingServiceImpl(adapter);

    @AfterEach
    void clearTenantContext() {
        id.osprey.iamservice.domain.tenant.TenantContext.clear();
    }

    @Test
    void error_delegatesWithErrorsCategory() {
        service.error("gagal total", 7L, "trace-1", Map.of("k", "v"));

        assertEquals(id.osprey.iamservice.domain.logging.LogCategory.ERRORS, captured().getCategory());
        assertEquals("gagal total", captured().getMessage());
        assertEquals(7L, captured().getTenantId());
        assertEquals("trace-1", captured().getTraceId());
        assertEquals(Map.of("k", "v"), captured().getAttributes());
    }

    @Test
    void general_integration_timer_email_delegateWithMatchingCategories() {
        service.general("umum", 1L, null, Map.of());
        service.integration("integrasi", 2L, null, Map.of());
        service.timer("durasi", 3L, null, Map.of());
        service.email("surat", 4L, null, Map.of());

        var entries = capturedAll();
        assertEquals(id.osprey.iamservice.domain.logging.LogCategory.GENERALS, entries.get(0).getCategory());
        assertEquals(id.osprey.iamservice.domain.logging.LogCategory.INTEGRATIONS, entries.get(1).getCategory());
        assertEquals(id.osprey.iamservice.domain.logging.LogCategory.TIMERS, entries.get(2).getCategory());
        assertEquals(id.osprey.iamservice.domain.logging.LogCategory.EMAILS, entries.get(3).getCategory());
        assertEquals(2L, entries.get(1).getTenantId());
        assertEquals(3L, entries.get(2).getTenantId());
        assertEquals(4L, entries.get(3).getTenantId());
    }

    @Test
    void write_fallsBackToTenantContext_whenTenantIdNotProvided() {
        id.osprey.iamservice.domain.tenant.TenantContext.setTenantId(99L);

        service.general("konteks", null, null, Map.of());

        assertEquals(99L, captured().getTenantId());
    }

    @Test
    void write_explicitTenantId_winsOverContext() {
        id.osprey.iamservice.domain.tenant.TenantContext.setTenantId(99L);

        service.general("eksplisit", 5L, null, Map.of());

        assertEquals(5L, captured().getTenantId());
    }

    @Test
    void write_stampsTimestampAndAttributes() {
        service.general("stempel", 1L, null, Map.of("a", 1));

        LogEntry entry = captured();

        assertNotNull(entry.getTimestamp());
        assertEquals(Map.of("a", 1), entry.getAttributes());
    }

    private LogEntry captured() {
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(adapter).write(captor.capture());
        return captor.getValue();
    }

    private java.util.List<LogEntry> capturedAll() {
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(adapter, org.mockito.Mockito.times(4)).write(captor.capture());
        return captor.getAllValues();
    }
}
