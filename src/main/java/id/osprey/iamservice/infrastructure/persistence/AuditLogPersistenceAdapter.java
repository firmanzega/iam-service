package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.audit.AuditLog;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Adapter yang memenuhi port AuditLogRepository memakai Spring Data JPA.
 */
@Repository
public class AuditLogPersistenceAdapter implements AuditLogRepository {

    private final SpringDataAuditLogRepository repository;

    public AuditLogPersistenceAdapter(SpringDataAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        AuditLogJpaEntity entity = new AuditLogJpaEntity(
                auditLog.getActorUserId(), auditLog.getAction(), auditLog.getTargetUserId(), auditLog.getDetails());

        return repository.save(entity).toDomain();
    }

    @Override
    public Page<AuditLog> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(AuditLogJpaEntity::toDomain);
    }
}
