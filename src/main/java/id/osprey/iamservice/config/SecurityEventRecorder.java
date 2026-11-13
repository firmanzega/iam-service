package id.osprey.iamservice.config;

import id.osprey.iamservice.application.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Mencatat penolakan keamanan ke log, metrik, dan jejak audit.
 *
 * Kenapa satu komponen khusus, bukan langsung di dua handler:
 *      - 401 dan 403 butuh perlakuan berbeda, tetapi keduanya harus tetap
 *        terhitung pada metrik yang sama agar operator bisa melihat lonjakan
 *        penolakan dari satu grafik.
 *      - Aturan "apa yang boleh disimpan" adalah keputusan keamanan, bukan
 *        detail penulisan respons HTTP. Menempatkannya di sini membuat aturan
 *        itu tidak tercecer di dua kelas.
 *
 * Kenapa 401 tidak disimpan ke jejak audit:
 *      - Permintaan tanpa token sah bisa dikirim siapa saja tanpa kredensial.
 *        Kalau setiap permintaan seperti itu menulis satu baris, tabel audit
 *        bisa dibanjiri trafik luar dan catatan yang penting justru tenggelam.
 *      - Yang tetap dibutuhkan operator adalah mengetahui bahwa penolakan
 *        terjadi. Untuk itu log WARN dan metrik sudah cukup.
 *
 * Kenapa 403 disimpan:
 *      - 403 hanya bisa terjadi setelah identitas terbukti sah. Artinya ada
 *        pengguna nyata yang mencoba melewati batas wewenang, dan itu justru
 *        peristiwa yang harus bisa ditelusuri sampai ke pelakunya.
 */
@Component
public class SecurityEventRecorder {

    /**
     * Nama metrik sengaja memakai awalan iam agar tidak bercampur dengan
     * metrik bawaan framework saat dibaca di dashboard.
     */
    static final String METRIC_NAME = "iam.security.rejected";

    private static final Logger log = LoggerFactory.getLogger(SecurityEventRecorder.class);
    private static final String ACTION_ACCESS_DENIED = "ACCESS_DENIED";

    /**
     * Jejak audit menyimpan teks bebas. Panjang dibatasi supaya URI yang
     * dimanipulasi pemanggil tidak bisa membuat satu baris membengkak.
     */
    private static final int MAX_DETAILS_LENGTH = 500;

    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public SecurityEventRecorder(AuditService auditService, MeterRegistry meterRegistry) {
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Mencatat permintaan yang tidak membawa kredensial sah.
     *
     * Alasan penolakan tidak memuat isi token atau pesan pustaka secara utuh,
     * karena pesan itu bisa memuat potongan kredensial yang tidak boleh masuk
     * log.
     */
    public void recordUnauthorized(HttpServletRequest request, String reason) {
        log.warn("Permintaan tanpa kredensial sah ditolak: method={} path={} alasan={}",
                request.getMethod(), request.getRequestURI(), reason);

        count("unauthorized", request.getMethod());
    }

    /**
     * Mencatat permintaan dari identitas sah yang tidak berwenang.
     *
     * Penyimpanan audit tidak boleh menggagalkan respons. Kalau database
     * sedang bermasalah, pemanggil tetap harus menerima 403 yang jelas,
     * bukan 500 yang menyesatkan.
     */
    public void recordAccessDenied(HttpServletRequest request, String reason) {
        Long actorUserId = currentActorUserId();

        log.warn("Akses ditolak: method={} path={} aktor={} alasan={}",
                request.getMethod(), request.getRequestURI(), actorUserId, reason);

        count("access_denied", request.getMethod());
        persistAccessDenied(actorUserId, request, reason);
    }

    private void count(String reason, String method) {
        meterRegistry.counter(METRIC_NAME, "reason", reason, "method", method).increment();
    }

    private void persistAccessDenied(Long actorUserId, HttpServletRequest request, String reason) {
        String details = truncate(request.getMethod() + " " + request.getRequestURI() + " ditolak: " + reason);

        try {
            auditService.recordIndependently(actorUserId, ACTION_ACCESS_DENIED, null, details);
        } catch (RuntimeException exception) {
            log.error("Gagal menyimpan jejak penolakan akses: method={} path={}",
                    request.getMethod(), request.getRequestURI(), exception);
        }
    }

    /**
     * Subjek token diisi dengan id pengguna pada JwtTokenService, sehingga
     * nama autentikasi adalah id tersebut. Nilai yang tidak bisa diurai
     * dikembalikan sebagai null, bukan ditebak, karena jejak audit tidak boleh
     * menunjuk pelaku yang salah.
     */
    private Long currentActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value.length() <= MAX_DETAILS_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_DETAILS_LENGTH);
    }
}
