package id.osprey.iamservice.application.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Ringkasan satu sesi untuk ditampilkan ke pemiliknya.
 *
 * @param id identitas sesi
 * @param deviceLabel label yang dikirim klien saat login, boleh null
 * @param createdAt waktu sesi dimulai
 * @param lastSeenAt waktu token refresh terakhir ditukar di sesi ini
 * @param expiresAt perkiraan akhir sesi bila tidak dipakai lagi
 * @param current true bila sesi ini milik token yang sedang dipakai memanggil
 */
public record SessionSummary(UUID id, String deviceLabel, Instant createdAt,
                             Instant lastSeenAt, Instant expiresAt, boolean current) {
}
