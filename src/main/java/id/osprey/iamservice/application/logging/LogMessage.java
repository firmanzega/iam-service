package id.osprey.iamservice.application.logging;

import id.osprey.iamservice.domain.logging.LoggingService;

import java.util.Map;

/**
 * Tindakan developer untuk mengirim pesan log kustom. Selalu menuju kategori
 * Generals sesuai ADR-0017; tidak membawa kategori sendiri karena saluran
 * kustom developer sudah ditetapkan sebagai Generals.
 *
 * Atribut disalin saat konstruksi dan disalin lagi saat dibaca supaya
 * pemanggil tidak bisa mengubah isi pesan yang sudah disusun (SpotBugs
 * EI_EXPOSE_REP).
 */
public record LogMessage(String message, Map<String, Object> attributes) {

    public LogMessage {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public LogMessage(String message) {
        this(message, Map.of());
    }

    @Override
    public Map<String, Object> attributes() {
        return Map.copyOf(attributes);
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
