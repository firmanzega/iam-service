package id.osprey.iamservice.domain.audit;

/**
 * Hasil pemeriksaan keutuhan rantai audit.
 *
 * @param valid true bila seluruh rantai utuh
 * @param checkedEntries jumlah entri yang diperiksa
 * @param firstBrokenEntryId id entri rusak pertama, null bila rantai utuh
 * @param reason kode alasan kerusakan, null bila rantai utuh
 */
public record AuditChainVerification(
        boolean valid,
        int checkedEntries,
        Long firstBrokenEntryId,
        String reason) {
}
