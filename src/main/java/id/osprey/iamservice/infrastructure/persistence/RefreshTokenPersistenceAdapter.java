package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.auth.RefreshToken;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Adapter yang memenuhi port RefreshTokenRepository memakai Spring Data JPA.
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
                ? new RefreshTokenJpaEntity(refreshToken.getTokenHash(), refreshToken.getExpiresAt())
                : repository.findById(refreshToken.getId()).orElseThrow();

        entity.setUser(userRepository.getReferenceById(refreshToken.getUser().getId()));

        if (refreshToken.getRevokedAt() != null) {
            entity.markRevokedAt(refreshToken.getRevokedAt());
        }

        return repository.save(entity).toDomain();
    }

    @Override
    public long revokeAllForUser(Long userId, Instant revokedAt) {
        return repository.revokeAllByUserId(userId, revokedAt);
    }

    @Override
    public long deleteExpiredBefore(Instant cutoff) {
        return repository.deleteExpiredBefore(cutoff);
    }
}
