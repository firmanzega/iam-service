package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.auth.Session;
import id.osprey.iamservice.domain.auth.SessionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter yang memenuhi port SessionRepository memakai Spring Data JPA.
 */
@Repository
public class SessionPersistenceAdapter implements SessionRepository {

    private final SpringDataSessionRepository repository;
    private final SpringDataUserRepository userRepository;

    public SessionPersistenceAdapter(SpringDataSessionRepository repository,
                                     SpringDataUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public Session save(Session session) {
        // Sesi baru memakai id dari domain, jadi baris lama dan baru dibedakan
        // lewat pencarian id, bukan lewat status "new" milik JPA.
        SessionJpaEntity entity = repository.findById(session.getId())
                .orElseGet(() -> new SessionJpaEntity(session.getId(), session.getCreatedAt()));

        entity.setUser(userRepository.getReferenceById(session.getUserId()));
        entity.setDeviceLabel(session.getDeviceLabel());
        entity.setLastSeenAt(session.getLastSeenAt());
        entity.setRevokedAt(session.getRevokedAt());

        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<Session> findById(UUID id) {
        return repository.findById(id).map(SessionJpaEntity::toDomain);
    }

    @Override
    public List<Session> findActiveByUser(Long userId, Instant activeSince) {
        return repository.findActiveByUserId(userId, activeSince).stream()
                .map(SessionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long revokeAllForUser(Long userId, Instant revokedAt) {
        return repository.revokeAllByUserId(userId, revokedAt);
    }

    @Override
    public long deleteInactiveBefore(Instant cutoff) {
        return repository.deleteInactiveBefore(cutoff);
    }
}
