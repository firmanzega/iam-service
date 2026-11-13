package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository Spring Data untuk entitas JPA sesi.
 */
public interface SpringDataSessionRepository extends JpaRepository<SessionJpaEntity, UUID> {

    /**
     * Sesi aktif milik pengguna, terbaru lebih dulu.
     *
     * Kenapa memakai batas waktu, bukan hanya revoked_at: sesi yang tidak
     * dipakai lebih lama dari masa berlaku token refresh tidak bisa
     * dilanjutkan lagi, jadi tidak perlu ditampilkan sebagai sesi hidup.
     */
    @Query("select session from SessionJpaEntity session "
            + "where session.user.id = :userId and session.revokedAt is null "
            + "and session.lastSeenAt >= :activeSince order by session.lastSeenAt desc")
    List<SessionJpaEntity> findActiveByUserId(@Param("userId") Long userId,
                                              @Param("activeSince") Instant activeSince);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SessionJpaEntity session set session.revokedAt = :revokedAt "
            + "where session.user.id = :userId and session.revokedAt is null")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Menghapus sesi yang sudah tidak mungkin dipakai lagi.
     *
     * Satu perintah delete dipakai supaya biayanya tetap datar, sama seperti
     * pembersihan token refresh.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SessionJpaEntity session "
            + "where (session.revokedAt is not null and session.revokedAt < :cutoff) "
            + "or (session.revokedAt is null and session.lastSeenAt < :cutoff)")
    int deleteInactiveBefore(@Param("cutoff") Instant cutoff);
}
