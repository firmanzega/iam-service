package id.osprey.iamservice.infrastructure.logging;

import id.osprey.iamservice.domain.logging.LogEntry;
import id.osprey.iamservice.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adaptor SLF4J/Logback untuk penulisan log terstruktur. Menginject tenantId
 * dan traceId ke MDC sehingga pola log output mencakup keduanya.
 *
 * Output format dikonfigurasikan melalui logback-spring.xml (JSON atau
 * key=value), bukan di kode ini.
 */
@Component
public class LoggingAdapter {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdapter.class);

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_TRACE_ID = "traceId";

    /**
     * Menulis entri log ke SLF4J. tenantId dan traceId dimasukkan ke MDC
     * untuk waktu pulang pergi pemanggilan ini, lalu dihapus agar tidak
     * bocor ke pemanggilan lain yang share thread yang sama.
     */
    public void write(LogEntry entry) {
        String previousTenantId = putMdc(MDC_TENANT_ID,
                entry.getTenantId() != null ? entry.getTenantId().toString() : null);
        String previousTraceId = putMdc(MDC_TRACE_ID, entry.getTraceId());

        try {
            switch (entry.getCategory()) {
                case ERRORS -> log.error(entry.getMessage());
                case GENERALS -> log.info(entry.getMessage());
                case INTEGRATIONS -> log.info(entry.getMessage());
                case TIMERS -> log.info(entry.getMessage());
                case EMAILS -> log.info(entry.getMessage());
            }
        } finally {
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
