package id.osprey.iamservice.domain.auth;

import java.time.Instant;
import java.util.Optional;

/**
 * Port untuk penyimpanan token refresh.
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    /**
     * Mencabut seluruh token refresh milik seorang pengguna.
     *
     * @param userId identitas pengguna
     * @return jumlah token yang dicabut
     */
    long revokeAllForUser(Long userId);

    /**
     * Menghapus token yang masa berlakunya sudah lewat.
     *
     * Token kedaluwarsa tidak bisa dipakai lagi, tetapi barisnya tetap ada di
     * database. Tanpa pembersihan berkala, tabel tumbuh terus dan query
     * pencarian token ikut melambat.
     *
     * @param cutoff batas waktu; token dengan expires_at sebelum nilai ini dihapus
     * @return jumlah baris yang dihapus
     */
    long deleteExpiredBefore(Instant cutoff);
}
