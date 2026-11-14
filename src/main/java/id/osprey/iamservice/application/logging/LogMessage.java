package id.osprey.iamservice.application.logging;

import id.osprey.iamservice.domain.logging.LogCategory;
import id.osprey.iamservice.domain.logging.LoggingService;

import java.util.Map;

/**
 * Tindakan developer untuk mengirim pesan log kustom ke LoggingService.
 * Secara default menggunakan kategori generals.
 */
public record LogMessage(LogCategory category, String message, Map<String, Object> attributes) {

    public LogMessage(String message) {
        this(LogCategory.GENERALS, message, Map.of());
    }

    /**
     * Mengirim pesan ini melalui LoggingService.
     *
     * @param loggingService layanan logging yang akan menangani pesan
     */
    public void send(LoggingService loggingService) {
        loggingService.general(message, null, null, attributes);
    }
}
