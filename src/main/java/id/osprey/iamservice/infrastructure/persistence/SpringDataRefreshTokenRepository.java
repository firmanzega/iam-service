package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data untuk entitas JPA token refresh.
 */
public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /**
     * Mencabut seluruh token aktif milik seorang pengguna dalam satu perintah.
     *
     * Kenapa bukan derived delete: menghapus baris membuat jejak token hilang,
     * padahal jejak itu yang dipakai untuk membedakan token yang dicabut dari
     * token yang tidak pernah ada. Satu update massal juga menghindari memuat
     * seluruh baris ke memori.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenJpaEntity token set token.revokedAt = :revokedAt "
            + "where token.user.id = :userId and token.revokedAt is null")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Mencabut seluruh token dalam satu sesi. Dipakai saat pengguna logout
     * dari satu perangkat atau mencabut sesi itu dari daftar perangkat.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenJpaEntity token set token.revokedAt = :revokedAt "
            + "where token.sessionId = :sessionId and token.revokedAt is null")
    int revokeAllBySessionId(@Param("sessionId") UUID sessionId,
                             @Param("revokedAt") Instant revokedAt);

    /**
     * Menghapus seluruh token kedaluwarsa dalam satu perintah.
     *
     * Kenapa bukan derived delete: Spring Data menjalankan select untuk memuat
     * semua baris lebih dulu, lalu mengirim satu delete per baris. Untuk
     * pembersihan berkala, cara itu membengkakkan memori dan jumlah round trip
     * seiring bertambahnya token. Satu perintah delete membuat biayanya tetap
     * datar.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshTokenJpaEntity token where token.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
