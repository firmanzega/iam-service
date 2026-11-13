package id.osprey.iamservice.domain.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port untuk penyimpanan token refresh.
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    /**
     * Mencabut seluruh token refresh milik seorang pengguna.
     *
     * Token yang sudah dicabut sebelumnya tidak diubah lagi, sehingga waktu
     * pencabutan pertama tetap menjadi bukti. Barisnya juga tidak dihapus:
     * jejak itu dipakai untuk mengenali pemakaian ulang token.
     *
     * @param userId identitas pengguna
     * @param revokedAt waktu pencabutan yang dicatat
     * @return jumlah token yang baru dicabut
     */
    long revokeAllForUser(Long userId, Instant revokedAt);

    /**
     * Mencabut seluruh token refresh dalam satu sesi.
     *
     * @param sessionId sesi yang dicabut
     * @param revokedAt waktu pencabutan yang dicatat
     * @return jumlah token yang baru dicabut
     */
    long revokeAllForSession(UUID sessionId, Instant revokedAt);

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
