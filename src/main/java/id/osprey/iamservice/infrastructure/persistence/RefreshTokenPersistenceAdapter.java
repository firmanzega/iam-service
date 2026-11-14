package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.auth.RefreshToken;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter yang memenuhi port RefreshTokenRepository memakai Spring Data JPA.
 * Setiap query difilter tenant_id sehingga token tidak pernah melintas tenant.
 */
@Repository
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository repository;
    private final SpringDataUserRepository userRepository;

    public RefreshTokenPersistenceAdapter(SpringDataRefreshTokenRepository repository,
                                          SpringDataUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        // Token yang sudah ada harus diubah, bukan disisipkan lagi. Kalau
        // disisipkan, batasan unik pada token_hash akan dilanggar.
        RefreshTokenJpaEntity entity = refreshToken.getId() == null
                ? new RefreshTokenJpaEntity(refreshToken.getTokenHash(),
                        refreshToken.getSessionId(), refreshToken.getExpiresAt())
                : repository.findById(refreshToken.getId()).orElseThrow();

        entity.setUser(userRepository.getReferenceById(refreshToken.getUser().getId()));
        entity.setTenantId(refreshToken.getTenantId());
        entity.setSessionId(refreshToken.getSessionId());

        if (refreshToken.getRevokedAt() != null) {
            entity.markRevokedAt(refreshToken.getRevokedAt());
        }

        return repository.save(entity).toDomain();
    }

    @Override
    public long revokeAllForUser(Long userId, Long tenantId, Instant revokedAt) {
        return repository.revokeAllByUserId(userId, tenantId, revokedAt);
    }

    @Override
    public long revokeAllForSession(UUID sessionId, Long tenantId, Instant revokedAt) {
        return repository.revokeAllBySessionId(sessionId, tenantId, revokedAt);
    }

    @Override
    public long deleteExpiredBefore(Long tenantId, Instant cutoff) {
        return repository.deleteExpiredBefore(tenantId, cutoff);
    }

    @Override
    public long deleteExpiredBefore(Instant cutoff) {
        return repository.deleteExpiredBeforeAll(cutoff);
    }
}
