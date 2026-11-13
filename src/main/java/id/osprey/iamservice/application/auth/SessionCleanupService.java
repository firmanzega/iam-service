package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.domain.auth.SessionRepository;
import id.osprey.iamservice.domain.auth.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case pembersihan sesi yang sudah tidak mungkin dipakai lagi.
 *
 * Kenapa dipisahkan dari pembersihan token refresh:
 *      - Token refresh dan sesi punya batas hidup yang berbeda. Token
 *        kedaluwarsa dihitung dari waktu terbitnya, sedangkan sesi berakhir
 *        bila tidak dipakai lebih lama dari masa berlaku token refresh.
 *      - Memisahkan aturannya membuat masing-masing bisa diuji tanpa
 *        menyalakan penjadwal.
 */
@Service
public class SessionCleanupService {

    private final SessionRepository sessionRepository;
    private final TokenSettings tokenSettings;

    public SessionCleanupService(SessionRepository sessionRepository, TokenSettings tokenSettings) {
        this.sessionRepository = sessionRepository;
        this.tokenSettings = tokenSettings;
    }

    /**
     * Menghapus sesi yang dicabut sudah lama atau tidak dipakai lebih lama
     * dari masa berlaku token refresh.
     *
     * @param now waktu acuan, dikirim dari pemanggil supaya bisa diuji
     * @return jumlah baris yang dihapus
     */
    @Transactional
    public long deleteInactive(Instant now) {
        Instant cutoff = now.minus(tokenSettings.refreshTokenTtl());
        return sessionRepository.deleteInactiveBefore(cutoff);
    }
}
