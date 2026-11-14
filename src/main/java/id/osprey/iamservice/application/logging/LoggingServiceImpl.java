package id.osprey.iamservice.application.logging;

import id.osprey.iamservice.domain.logging.LogCategory;
import id.osprey.iamservice.domain.logging.LogEntry;
import id.osprey.iamservice.domain.logging.LoggingService;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.infrastructure.logging.LoggingAdapter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Implementasi LoggingService. Menyusun LogEntry dengan tenantId dari
 * TenantContext (bila caller tidak menyediakannya), lalu mendelegasikan
 * penulisan ke LoggingAdapter (SLF4J/Logback dengan MDC).
 */
@Service
public class LoggingServiceImpl implements LoggingService {

    private final LoggingAdapter adapter;

    public LoggingServiceImpl(LoggingAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void error(String message, Long tenantId, String traceId, Map<String, Object> attributes) {
        write(LogCategory.ERRORS, message, tenantId, traceId, attributes);
    }

    @Override
    public void general(String message, Long tenantId, String traceId, Map<String, Object> attributes) {
        write(LogCategory.GENERALS, message, tenantId, traceId, attributes);
    }

    @Override
    public void integration(String message, Long tenantId, String traceId, Map<String, Object> attributes) {
        write(LogCategory.INTEGRATIONS, message, tenantId, traceId, attributes);
    }

    @Override
    public void timer(String message, Long tenantId, String traceId, Map<String, Object> attributes) {
        write(LogCategory.TIMERS, message, tenantId, traceId, attributes);
    }

    @Override
    public void email(String message, Long tenantId, String traceId, Map<String, Object> attributes) {
        write(LogCategory.EMAILS, message, tenantId, traceId, attributes);
    }

    private void write(LogCategory category, String message, Long tenantId, String traceId,
                       Map<String, Object> attributes) {
        Long resolvedTenantId = tenantId != null ? tenantId : TenantContext.getTenantId();
        adapter.write(new LogEntry(category, message, resolvedTenantId, traceId, Instant.now(), attributes));
    }
}
