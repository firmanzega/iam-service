package id.osprey.iamservice.domain.audit;

import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;

/**
 * Port untuk penyimpanan jejak audit.
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    PageResult<AuditLog> findAll(PaginationRequest request);
}
