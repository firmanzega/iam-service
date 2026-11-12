package id.osprey.iamservice.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Port untuk penyimpanan jejak audit.
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    Page<AuditLog> findAll(Pageable pageable);
}
