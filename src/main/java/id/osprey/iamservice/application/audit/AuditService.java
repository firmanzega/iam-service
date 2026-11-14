package id.osprey.iamservice.application.audit;

import id.osprey.iamservice.domain.audit.AuditLog;
import id.osprey.iamservice.domain.audit.AuditChainVerification;
import id.osprey.iamservice.domain.audit.AuditChainVerifier;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.domain.audit.AuditLogSortableProperties;
import id.osprey.iamservice.domain.tenant.Tenant;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.tenant.TenantRepository;
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

    private static final String DEFAULT_ORGANIZATION_ID = "org-default";

    private final AuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        TenantRepository tenantRepository) {
        this.auditLogRepository = auditLogRepository;
        this.tenantRepository = tenantRepository;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        var existing = tenantRepository.findByOrganizationId(DEFAULT_ORGANIZATION_ID);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        try {
            Tenant t = tenantRepository.save(new Tenant(null, "Default Tenant", DEFAULT_ORGANIZATION_ID, true, null));
            return t.getId();
        } catch (Exception e) {
            // Tenant already exists (unique constraint on name/organization_id)
            return tenantRepository.findByOrganizationId(DEFAULT_ORGANIZATION_ID)
                    .map(Tenant::getId)
                    .orElseThrow(() -> new IllegalStateException("Tenant default tidak tersedia"));
        }
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
        auditLogRepository.save(new AuditLog(actorUserId, currentTenantId(), action,
                targetUserId, details));
    }

    /**
     * Memeriksa keutuhan seluruh rantai jejak audit.
     *
     * Pemeriksaan dijalankan di memori setelah seluruh baris dibaca, karena
     * yang dibutuhkan adalah laporan entri rusak pertama, bukan satu query
     * yang rumit. Untuk jumlah baris yang sangat besar, pemeriksaan ini lebih
     * tepat dipindahkan ke proses terjadwal.
     */
    @Transactional(readOnly = true)
    public AuditChainVerification verifyChain() {
        return AuditChainVerifier.verify(auditLogRepository.findAllForChainVerification());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(Long actorUserId, String action, Long targetUserId, String details) {
        auditLogRepository.save(new AuditLog(actorUserId, currentTenantId(), action,
                targetUserId, details));
    }
}
