package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import id.osprey.iamservice.domain.auth.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Menangani bukti pemakaian ulang token refresh.
 *
 * Kenapa kelas terpisah, bukan method di AuthService:
 *      - Pemakaian ulang terjadi di tengah alur refresh yang akan berakhir
 *        dengan galat 401. Transaksi refresh itu dibatalkan, sehingga
 *        pencabutan dan catatan auditnya ikut hilang kalau memakai transaksi
 *        yang sama.
 *      - Anotasi transaksi hanya bekerja lewat proxy Spring. Kalau method ini
 *        dipanggil dari dalam AuthService sendiri, anotasinya tidak berlaku.
 *        Kelas terpisah memastikan transaksi baru benar-benar dibuat.
 *
 * Kebijakannya sengaja tegas: token yang sudah dicabut lalu dipakai lagi
 * dianggap bukti pencurian, sehingga seluruh sesi pengguna dicabut. Pemilik
 * token yang sah harus login ulang, tetapi pencuri juga kehilangan akses.
 */
@Service
public class RefreshTokenReuseService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenReuseService.class);

    private static final String AUDIT_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";

    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final AuditService auditService;

    public RefreshTokenReuseService(RefreshTokenRepository refreshTokenRepository,
                                    SessionRepository sessionRepository,
                                    AuditService auditService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
    }

    /**
     * Mencabut seluruh token refresh pengguna dan mencatat kejadiannya.
     *
     * Catatan audit ikut transaksi ini, bukan transaksi terpisah lagi. Kalau
     * pencabutan gagal, catatan yang mengatakan sesi sudah dicabut tidak boleh
     * ikut tersimpan.
     *
     * @param userId pemilik token yang dipakai ulang
     * @return jumlah token yang baru dicabut
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long revokeAllAfterReuse(Long userId) {
        Instant now = Instant.now();
        long revoked = refreshTokenRepository.revokeAllForUser(userId, now);

        // Sesi ikut dicabut supaya daftar perangkat tidak menampilkan sesi yang
        // tokennya sudah tidak bisa diperpanjang lagi.
        sessionRepository.revokeAllForUser(userId, now);

        auditService.record(userId, AUDIT_TOKEN_REUSE, userId,
                "token refresh yang sudah dicabut dipakai lagi; " + revoked
                        + " token aktif dicabut");

        log.warn("Refresh token reuse detected for user {}; {} active tokens revoked", userId, revoked);
        return revoked;
    }
}
