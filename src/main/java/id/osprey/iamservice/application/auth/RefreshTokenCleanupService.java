package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case pembersihan token refresh yang sudah kedaluwarsa.
 *
 * Kenapa perlu dipisahkan dari penjadwal:
 *      - Aturan "token mana yang boleh dihapus" adalah urusan aplikasi,
 *        sedangkan kapan pembersihan dijalankan adalah urusan infrastruktur.
 *      - Dengan pemisahan ini, aturan penghapusan bisa diuji tanpa menyalakan
 *        penjadwal Spring.
 *
 * Kenapa token yang sudah dicabut tidak langsung dihapus:
 *      - Token yang dicabut masih berguna sebagai bukti bila nanti ditambahkan
 *        deteksi pemakaian ulang. Baris itu baru dibuang setelah masa
 *        berlakunya benar-benar lewat.
 */
@Service
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Menghapus seluruh token yang masa berlakunya sudah lewat.
     *
     * @param now waktu acuan, dikirim dari pemanggil supaya bisa diuji
     * @return jumlah baris yang dihapus
     */
    @Transactional
    public long deleteExpired(Instant now) {
        return refreshTokenRepository.deleteExpiredBefore(now);
    }
}
