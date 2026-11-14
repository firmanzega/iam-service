package id.osprey.iamservice.domain.logging;

import java.time.Instant;
import java.util.Map;

/**
 * Entri log terstruktur yang dibawa oleh LoggingService. Setiap entri
 * mencakup kategori, pesan, tenantId (dari TenantContext via MDC), dan
 * traceId (dari OpenTelemetry bila sampling aktif).
 */
public class LogEntry {

    private final LogCategory category;
    private final String message;
    private final Long tenantId;
    private final String traceId;
    private final Instant timestamp;
    private final Map<String, Object> attributes;

    public LogEntry(LogCategory category, String message, Long tenantId, String traceId,
                    Instant timestamp, Map<String, Object> attributes) {
        this.category = category;
        this.message = message;
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.timestamp = timestamp;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public LogCategory getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
