package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository Spring Data untuk entitas JPA token refresh.
 */
public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    long deleteByUserIdAndRevokedAtIsNull(Long userId);

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
