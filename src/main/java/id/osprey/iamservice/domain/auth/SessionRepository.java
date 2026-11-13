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

    Optional<Session> findById(UUID id);

    /**
     * Sesi aktif milik seorang pengguna, terbaru lebih dulu.
     *
     * @param userId pemilik sesi
     * @param activeSince batas waktu; sesi yang terakhir dipakai sebelum ini
     *                    sudah tidak bisa dilanjutkan karena token refresh-nya
     *                    kedaluwarsa
     */
    List<Session> findActiveByUser(Long userId, Instant activeSince);

    /**
     * Mencabut seluruh sesi aktif milik pengguna. Dipakai saat seluruh akses
     * harus diputus, misalnya kata sandi berubah atau akun dinonaktifkan.
     */
    long revokeAllForUser(Long userId, Instant revokedAt);

    /**
     * Menghapus sesi yang sudah tidak mungkin dipakai lagi: yang dicabut sudah
     * lama, atau yang tidak dipakai lebih lama dari masa berlaku token refresh.
     */
    long deleteInactiveBefore(Instant cutoff);
}
