package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository Spring Data untuk entitas JPA jejak audit.
 */
public interface SpringDataAuditLogRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    Page<AuditLogJpaEntity> findAll(Pageable pageable);

    /**
     * Semua jejak audit dalam satu tenant, untuk daftar jejak yang difilter
     * tenant.
     */
    @Query("select a from AuditLogJpaEntity a where a.tenantId = :tenantId")
    Page<AuditLogJpaEntity> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    List<AuditLogJpaEntity> findAllByOrderByIdAsc();
}
