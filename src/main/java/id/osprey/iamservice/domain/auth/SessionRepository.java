package id.osprey.iamservice.domain.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port untuk penyimpanan sesi perangkat.
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(UUID id, Long tenantId);

    /**
     * Sesi aktif milik seorang pengguna dalam satu tenant, terbaru lebih dulu.
     *
     * @param userId pemilik sesi
     * @param tenantId tenant internal pemilik sesi
     * @param activeSince batas waktu; sesi yang terakhir dipakai sebelum ini
     *                    sudah tidak bisa dilanjutkan karena token refresh-nya
     *                    kedaluwarsa
     */
    List<Session> findActiveByUser(Long userId, Long tenantId, Instant activeSince);

    /**
     * Mencabut seluruh sesi aktif milik pengguna dalam satu tenant. Dipakai saat
     * seluruh akses harus diputus, misalnya kata sandi berubah atau akun
     * dinonaktifkan.
     */
    long revokeAllForUser(Long userId, Long tenantId, Instant revokedAt);

    /**
     * Menghapus sesi yang sudah tidak mungkin dipakai lagi dalam satu tenant:
     * yang dicabut sudah lama, atau yang tidak dipakai lebih lama dari masa
     * berlaku token refresh.
     */
    long deleteInactiveBefore(Long tenantId, Instant cutoff);

    /**
     * Menghapus sesi yang sudah tidak mungkin dipakai lagi di seluruh tenant.
     * Dipakai oleh penjadwal pembersihan yang tidak berjalan dalam konteks
     * permintaan tenant tertentu.
     */
    long deleteInactiveBefore(Instant cutoff);
}
