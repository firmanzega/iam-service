package id.osprey.iamservice.config;

import id.osprey.iamservice.application.auth.SessionCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Penjadwal pembersihan sesi perangkat yang sudah tidak aktif.
 *
 * Kenapa dijalankan berkala: setiap login menambah satu baris sesi. Tanpa
 * pembersihan, daftar perangkat menyimpan riwayat selamanya dan indeksnya
 * membesar. Sesi yang dicabut atau tidak dipakai lebih lama dari masa berlaku
 * token refresh sudah tidak bisa dilanjutkan, jadi barisnya aman dihapus.
 */
@Component
@ConditionalOnProperty(prefix = "iam.session-cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);

    private final SessionCleanupService cleanupService;

    public SessionCleanupJob(SessionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${iam.session-cleanup.interval:PT1H}")
    public void deleteInactiveSessions() {
        long deleted = cleanupService.deleteInactive(Instant.now());
        if (deleted > 0) {
            log.info("Menghapus {} sesi perangkat yang sudah tidak aktif", deleted);
        }
    }
}
