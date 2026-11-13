package id.osprey.iamservice.domain.audit;

import java.time.Instant;

/**
 * Satu mata rantai jejak audit, lengkap dengan hash yang tersimpan.
 *
 * Kelas ini terpisah dari AuditLog karena hanya dipakai saat memeriksa
 * keutuhan rantai. Daftar audit biasa tidak perlu membawa hash, sehingga
 * bentuk balasan yang sudah ada tidak berubah.
 *
 * @param id identitas baris
 * @param actorUserId pelaku perubahan, boleh null untuk sistem
 * @param action jenis kejadian
 * @param targetUserId objek yang diubah, boleh null
 * @param details rincian bebas
 * @param createdAt waktu kejadian
 * @param prevHash hash entri sebelumnya, null hanya untuk entri pertama
 * @param entryHash hash entri ini
 */
public record AuditChainEntry(
        Long id,
        Long actorUserId,
        String action,
        Long targetUserId,
        String details,
        Instant createdAt,
        String prevHash,
        String entryHash) {
}
