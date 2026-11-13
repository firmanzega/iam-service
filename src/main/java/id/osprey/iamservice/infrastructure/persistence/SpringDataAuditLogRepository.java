package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository Spring Data untuk entitas JPA jejak audit.
 */
public interface SpringDataAuditLogRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    Page<AuditLogJpaEntity> findAll(Pageable pageable);

    List<AuditLogJpaEntity> findAllByOrderByIdAsc();
}
