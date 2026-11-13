package id.osprey.iamservice.domain.audit;

import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;

import java.util.List;

/**
 * Port untuk penyimpanan jejak audit.
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    PageResult<AuditLog> findAll(PaginationRequest request);

    /**
     * Membaca seluruh entri beserta hash-nya untuk pemeriksaan rantai.
     *
     * Operasi ini sengaja terpisah dari findAll karena tidak memakai halaman
     * dan tidak boleh dipakai untuk menyajikan data biasa.
     *
     * @return seluruh entri, diurutkan menurut id menaik
     */
    List<AuditChainEntry> findAllForChainVerification();
}
