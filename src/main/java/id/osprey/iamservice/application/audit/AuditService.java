package id.osprey.iamservice.application.audit;

import id.osprey.iamservice.domain.audit.AuditLog;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case pencatatan jejak audit.
 *
 * Ada dua cara pencatatan, dan pilihannya penting:
 *      - record: ikut transaksi yang sedang berjalan. Dipakai untuk aksi yang
 *        sukses, sehingga perubahan bisnis dan catatannya committed
 *        bersamaan. Tidak mungkin ada perubahan tanpa catatan.
 *      - recordIndependently: memakai transaksi terpisah. Dipakai untuk aksi
 *        yang gagal, karena transaksinya sendiri dibatalkan. Kalau catatannya
 *        ikut di transaksi yang sama, jejaknya hilang dan percobaan serangan
 *        justru tidak terekam.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Membaca jejak audit dalam bentuk halaman.
     *
     * Kenapa lewat use case, bukan repository langsung: aturan siapa yang
     * boleh membaca dan bagaimana data disajikan tetap milik application.
     * Controller cukup menerjemahkan HTTP, sehingga penyimpanan bisa diganti
     * tanpa menyentuh lapisan web.
     */
    @Transactional(readOnly = true)
    public PageResult<AuditLog> findAll(PaginationRequest request) {
        return auditLogRepository.findAll(request);
    }

    @Transactional
    public void record(Long actorUserId, String action, Long targetUserId, String details) {
        auditLogRepository.save(new AuditLog(actorUserId, action, targetUserId, details));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(Long actorUserId, String action, Long targetUserId, String details) {
        auditLogRepository.save(new AuditLog(actorUserId, action, targetUserId, details));
    }
}
