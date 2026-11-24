package id.osprey.iamservice.infrastructure.logging;

import id.osprey.iamservice.domain.logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SLF4J/Logback adapter for structured log entries. It injects tenantId,
 * traceId, and logCategory into MDC for the duration of one write, then
 * restores the previous values so thread-local state does not leak.
 *
 * The console format is configured by logging.structured.format.console
 * (ECS JSON), not by this class.
 */
@Component
public final class LoggingAdapter {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdapter.class);

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_LOG_CATEGORY = "logCategory";

    /**
     * Writes one log entry through SLF4J.
     *
     * @param entry log entry carrying category, message, tenant id, and optional trace id
     */
    public void write(LogEntry entry) {
        String previousTenantId = putMdc(MDC_TENANT_ID,
                entry.getTenantId() != null ? entry.getTenantId().toString() : null);
        String previousTraceId = putMdc(MDC_TRACE_ID, entry.getTraceId());
        String previousCategory = putMdc(MDC_LOG_CATEGORY, entry.getCategory().name());

        try {
            switch (entry.getCategory()) {
                case ERRORS -> log.error(entry.getMessage());
                case GENERALS -> log.info(entry.getMessage());
                case INTEGRATIONS -> log.info(entry.getMessage());
                case TIMERS -> log.info(entry.getMessage());
                case EMAILS -> log.info(entry.getMessage());
            }
        } finally {
            removeMdc(MDC_LOG_CATEGORY, previousCategory);
            removeMdc(MDC_TENANT_ID, previousTenantId);
            removeMdc(MDC_TRACE_ID, previousTraceId);
        }
    }

    private static String putMdc(String key, String value) {
        String previous = org.slf4j.MDC.get(key);
        if (value != null) {
            org.slf4j.MDC.put(key, value);
        } else {
            org.slf4j.MDC.remove(key);
        }
        return previous;
    }

    private static void removeMdc(String key, String previous) {
        if (previous != null) {
            org.slf4j.MDC.put(key, previous);
        } else {
            org.slf4j.MDC.remove(key);
        }
    }
}
