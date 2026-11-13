package id.osprey.iamservice.config;

import id.osprey.iamservice.application.security.SecurityRejectionStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Penjadwal pembersihan agregat penolakan 401.
 *
 * Agregat ini berguna untuk melihat lonjakan dalam hitungan menit, bukan untuk
 * arsip jangka panjang. Tanpa pembersihan, tabelnya tumbuh terus meskipun
 * nilainya sudah tidak dipakai. Retensi dan selang waktu dibaca dari
 * konfigurasi supaya operator bisa memendekkannya saat penyimpanan terbatas.
 */
@Component
@ConditionalOnProperty(prefix = "iam.security-rejection-stats", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SecurityRejectionStatsCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SecurityRejectionStatsCleanupJob.class);

    private final SecurityRejectionStatsService statsService;
    private final IamProperties properties;

    public SecurityRejectionStatsCleanupJob(SecurityRejectionStatsService statsService,
                                            IamProperties properties) {
        this.statsService = statsService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${iam.security-rejection-stats.interval:PT1H}")
    public void deleteExpiredStats() {
        long deleted = statsService.deleteOlderThan(
                Instant.now(), properties.securityRejectionStats().retention());

        if (deleted > 0) {
            log.info("Menghapus {} baris agregat penolakan yang sudah melewati retensi", deleted);
        }
    }
}
