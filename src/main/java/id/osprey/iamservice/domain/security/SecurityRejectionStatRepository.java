package id.osprey.iamservice.domain.security;

import java.time.Instant;

/**
 * Port untuk agregat penolakan autentikasi.
 *
 * Kenapa hanya jumlah per jendela waktu, method, dan alasan:
 *      - Path dan alamat IP dikendalikan pemanggil. Kalau keduanya dijadikan
 *        kunci agregat, penyerang bisa mengirim nilai acak dan membuat jumlah
 *        baris tumbuh tanpa batas. Agregat seperti itu justru bisa dipakai
 *        untuk menghabiskan penyimpanan.
 *      - Yang dibutuhkan operator adalah besaran dan waktunya. Detail satu
 *        peristiwa tetap ada di log.
 */
public interface SecurityRejectionStatRepository {

    /**
     * Menambah satu kejadian pada jendela waktu tertentu dalam satu tenant.
     *
     * @param tenantId tenant internal
     * @param windowStart awal jendela, sudah dipotong ke batas yang dipakai
     * @param httpMethod method permintaan, misalnya GET atau POST
     * @param reason alasan penolakan, misalnya unauthorized
     * @param seenAt waktu kejadian
     */
    void increment(Long tenantId, Instant windowStart, String httpMethod, String reason, Instant seenAt);

    /**
     * Menghapus agregat yang jendelanya lebih lama dari batas retensi dalam
     * satu tenant.
     *
     * @param tenantId tenant internal
     * @param cutoff jendela yang dimulai sebelum waktu ini dihapus
     * @return jumlah baris yang dihapus
     */
    long deleteOlderThan(Long tenantId, Instant cutoff);

    /**
     * Menghapus agregat yang jendelanya lebih lama dari batas retensi di
     * seluruh tenant. Dipakai oleh penjadwal pembersihan yang tidak berjalan
     * dalam konteks permintaan tenant tertentu.
     */
    long deleteOlderThan(Instant cutoff);
}
