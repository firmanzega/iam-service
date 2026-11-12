package id.osprey.iamservice.config;

import id.osprey.iamservice.application.auth.RefreshTokenCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Penjadwal pembersihan token refresh yang sudah kedaluwarsa.
 *
 * Kenapa dijalankan berkala: setiap login dan penukaran token menambah baris
 * baru di refresh_tokens. Tanpa pembersihan, tabel itu tumbuh tanpa batas,
 * indeksnya makin besar, dan pencarian token lambat laun melambat.
 *
 * Selang waktu bisa diatur lewat iam.refresh-token-cleanup.interval, dan
 * penjadwalnya bisa dimatikan lewat iam.refresh-token-cleanup.enabled. Nilai
 * bawaan satu jam dipilih supaya beban hapusnya kecil dan tidak menumpuk.
 */
@Component
@ConditionalOnProperty(prefix = "iam.refresh-token-cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenCleanupService cleanupService;

    public RefreshTokenCleanupJob(RefreshTokenCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${iam.refresh-token-cleanup.interval:PT1H}")
    public void deleteExpiredTokens() {
        long deleted = cleanupService.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Menghapus {} token refresh yang sudah kedaluwarsa", deleted);
        }
    }
}
