package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.audit.AuditChainEntry;
import id.osprey.iamservice.domain.audit.AuditLog;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

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
                auditLog.getActorUserId(), auditLog.getTenantId(), auditLog.getAction(),
                auditLog.getTargetUserId(), auditLog.getDetails());

        return repository.save(entity).toDomain();
    }

    @Override
    public PageResult<AuditLog> findAll(PaginationRequest request) {
        return PaginationTranslator.toPageResult(
                repository.findAllByTenantId(TenantContext.getTenantId(),
                        PaginationTranslator.toPageable(request)), AuditLogJpaEntity::toDomain);
    }

    @Override
    public List<AuditChainEntry> findAllForChainVerification() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(AuditLogJpaEntity::toChainEntry)
                .toList();
    }
}
