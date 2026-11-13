package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.domain.audit.AuditChainVerification;

/**
 * Hasil pemeriksaan rantai jejak audit.
 *
 * @param valid true bila seluruh rantai utuh
 * @param checkedEntries jumlah entri yang diperiksa
 * @param firstBrokenEntryId id entri rusak pertama, null bila utuh
 * @param reason kode alasan kerusakan, null bila utuh
 */
public record AuditChainVerificationResponse(
        boolean valid,
        int checkedEntries,
        Long firstBrokenEntryId,
        String reason) {

    public static AuditChainVerificationResponse from(AuditChainVerification verification) {
        return new AuditChainVerificationResponse(
                verification.valid(),
                verification.checkedEntries(),
                verification.firstBrokenEntryId(),
                verification.reason());
    }
}
