package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository Spring Data untuk entitas JPA token refresh.
 */
public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    long deleteByUserIdAndRevokedAtIsNull(Long userId);

    long deleteByExpiresAtBefore(Instant cutoff);
}
