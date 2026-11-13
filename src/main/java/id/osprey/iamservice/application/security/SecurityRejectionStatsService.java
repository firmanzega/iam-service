package id.osprey.iamservice.application.security;

import id.osprey.iamservice.domain.security.SecurityRejectionStatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Use case agregat penolakan autentikasi.
 *
 * Kenapa pencatatan memakai transaksi baru:
 *      - Pencatatan berjalan di luar transaksi bisnis, tepatnya saat permintaan
 *        ditolak sebelum controller dipanggil. Kalau nanti pemanggilnya berada
 *        di dalam transaksi yang akan dibatalkan, catatan penolakan tidak boleh
 *        ikut hilang.
 *      - Transaksi baru juga memastikan kegagalan pencatatan hanya membatalkan
 *        baris agregat itu sendiri, bukan pekerjaan utama pemanggil.
 *
 * Kenapa jendela waktu dipotong ke menit: satu baris mewakili satu menit, satu
 * method, dan satu alasan. Jumlah barisnya terbatas, sehingga penyerang tidak
 * bisa membuat tabel membengkak dengan mengirim path atau alamat acak.
 */
@Service
public class SecurityRejectionStatsService {

    private final SecurityRejectionStatRepository repository;

    public SecurityRejectionStatsService(SecurityRejectionStatRepository repository) {
        this.repository = repository;
    }

    /**
     * Menambah satu kejadian pada jendela waktu yang memuat {@code seenAt}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Instant seenAt, String httpMethod, String reason) {
        Instant windowStart = seenAt.truncatedTo(ChronoUnit.MINUTES);
        repository.increment(windowStart, httpMethod, reason, seenAt);
    }

    /**
     * Menghapus agregat yang sudah melewati masa retensi.
     *
     * @param now       waktu acuan
     * @param retention lama agregat disimpan
     * @return jumlah baris yang dihapus
     */
    @Transactional
    public long deleteOlderThan(Instant now, Duration retention) {
        return repository.deleteOlderThan(now.minus(retention));
    }
}
