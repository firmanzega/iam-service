package id.osprey.iamservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Konfigurasi khusus layanan ini. Dipisah dari konfigurasi bawaan Spring Boot
 * supaya semua parameter yang bisa diubah operator terkumpul di satu tempat.
 *
 * @param jwt konfigurasi penerbitan token
 * @param cors daftar origin yang diizinkan untuk memanggil API
 * @param login aturan penguncian akun setelah gagal login berulang
 * @param rateLimit batas jumlah permintaan per jendela waktu
 */
@ConfigurationProperties(prefix = "iam")
public record IamProperties(Jwt jwt, Cors cors, Login login, RateLimit rateLimit) {

    /**
     * @param secret kunci penanda tangan HMAC, panjang minimal 32 byte.
     * @param issuer identitas penerbit yang ditulis ke claim "iss".
     * @param accessTokenTtl masa berlaku token akses.
     * @param refreshTokenTtl masa berlaku token refresh.
     */
    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    /**
     * @param allowedOrigins origin frontend yang diizinkan. Tidak boleh memakai
     *                      tanda bintang karena kredensial ikut dikirim.
     */
    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * @param maxFailedAttempts jumlah kegagalan login sebelum akun dikunci.
     * @param lockDuration lama akun terkunci.
     */
    public record Login(int maxFailedAttempts, Duration lockDuration) {
    }

    /**
     * @param maxRequests jumlah permintaan maksimum dalam satu jendela waktu.
     * @param window panjang jendela waktu.
     */
    public record RateLimit(int maxRequests, Duration window) {
    }
}
