package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data untuk entitas JPA jejak audit.
 */
public interface SpringDataAuditLogRepository extends JpaRepository<AuditLogJpaEntity, Long> {
}
