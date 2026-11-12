package id.osprey.iamservice.application.auth;

import java.util.List;

/**
 * Hasil login yang siap dikirim ke pemanggil.
 *
 * @param accessToken token untuk dipakai pada header Authorization
 * @param refreshToken token untuk ditukar menjadi token akses baru
 * @param expiresIn sisa masa berlaku token akses dalam detik
 * @param roles role yang dimiliki pengguna
 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn, List<String> roles) {
}
