package id.osprey.iamservice.application.audit;

import id.osprey.iamservice.domain.audit.AuditLog;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.domain.audit.AuditLogSortableProperties;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import id.osprey.iamservice.domain.shared.SortSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
     *
     * Kenapa urutan bawaan ditegakkan di sini: pemanggil yang tidak mengirim
     * parameter sort tetap harus mendapat urutan yang stabil. Tanpa urutan,
     * database bebas mengembalikan baris dalam urutan apa pun, sehingga
     * perpindahan halaman bisa menduplikasi atau melewatkan catatan. Urutan
     * bawaan adalah yang terbaru lebih dulu, dengan id sebagai pemecah seri
     * karena beberapa catatan bisa lahir pada waktu yang sama.
     */
    @Transactional(readOnly = true)
    public PageResult<AuditLog> findAll(PaginationRequest request) {
        PaginationRequest normalized = withDefaultSort(request);
        validateSort(normalized);

        return auditLogRepository.findAll(normalized);
    }

    /**
     * Menambahkan urutan bawaan hanya bila pemanggil tidak meminta urutan
     * sendiri. Urutan eksplisit tetap dihormati, termasuk arah menaik.
     */
    private PaginationRequest withDefaultSort(PaginationRequest request) {
        if (!request.sort().isEmpty()) {
            return request;
        }

        return new PaginationRequest(request.page(), request.size(), List.of(
                new SortSpec("createdAt", true),
                new SortSpec("id", true)));
    }

    /**
     * Menolak properti pengurutan yang tidak dikenal sebelum query dijalankan.
     * Dengan begitu pemanggil menerima 400 yang menjelaskan masalahnya, bukan
     * 500 dari database.
     */
    private void validateSort(PaginationRequest request) {
        List<String> rejected = request.sort().stream()
                .map(SortSpec::property)
                .filter(property -> !AuditLogSortableProperties.ALLOWED.contains(property))
                .distinct()
                .toList();

        if (!rejected.isEmpty()) {
            throw new DomainException(DomainException.Reason.INVALID_SORT_PROPERTY,
                    "Properties not allowed for sorting: " + rejected
                            + ". Allowed: " + AuditLogSortableProperties.ALLOWED);
        }
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
