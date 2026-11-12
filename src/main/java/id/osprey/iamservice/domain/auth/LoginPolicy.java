package id.osprey.iamservice.domain.auth;

import java.time.Duration;

/**
 * Aturan penguncian akun setelah gagal login berulang.
 *
 * @param maxFailedAttempts jumlah kegagalan login sebelum akun dikunci
 * @param lockDuration lama akun terkunci
 */
public record LoginPolicy(int maxFailedAttempts, Duration lockDuration) {
}
