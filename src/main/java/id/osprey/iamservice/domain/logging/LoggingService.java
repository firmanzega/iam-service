package id.osprey.iamservice.domain.logging;

import java.util.Map;

/**
 * Port untuk pencatatan log terstruktur berdasarkan kategori. Implementasi
 * nyata ditangani oleh LoggingAdapter (SLF4J/Logback dengan MDC).
 */
public interface LoggingService {

    void error(String message, Long tenantId, String traceId, Map<String, Object> attributes);

    void general(String message, Long tenantId, String traceId, Map<String, Object> attributes);

    void integration(String message, Long tenantId, String traceId, Map<String, Object> attributes);

    void timer(String message, Long tenantId, String traceId, Map<String, Object> attributes);

    void email(String message, Long tenantId, String traceId, Map<String, Object> attributes);
}
