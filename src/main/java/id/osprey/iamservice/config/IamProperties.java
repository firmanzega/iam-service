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
 * @param refreshTokenCleanup pembersihan token refresh yang sudah kedaluwarsa
 */
@ConfigurationProperties(prefix = "iam")
public record IamProperties(Jwt jwt, Cors cors, Login login, RateLimit rateLimit,
                            RefreshTokenCleanup refreshTokenCleanup) {

    /**
     * @param secret kunci penanda tangan HMAC, panjang minimal 32 byte.
     * @param issuer identitas penerbit yang ditulis ke claim "iss".
     * @param accessTokenTtl masa berlaku token akses.
     * @param refreshTokenTtl masa berlaku token refresh.
     * @param previousSecret kunci sebelumnya yang masih diterima selama masa
     *                       transisi rotasi, boleh null bila tidak ada rotasi.
     */
    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl,
                      String previousSecret) {

        // Nilai kosong dari environment variable diperlakukan sama seperti
        // tidak diisi. Tanpa ini, string kosong akan dianggap kunci dan
        // aplikasi gagal start dengan pesan yang membingungkan.
        public Jwt {
            previousSecret = previousSecret == null || previousSecret.isBlank() ? null : previousSecret;
        }
    }

    /**
     * @param allowedOrigins origin frontend yang diizinkan. Tidak boleh memakai
     *                      tanda bintang karena kredensial ikut dikirim.
     */
    public record Cors(List<String> allowedOrigins) {

        // Konfigurasi boleh dibaca berkali-kali oleh lapisan lain. Salinan
        // yang tidak bisa diubah menjaga nilai konfigurasi tetap sama.
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
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
     * @param cleanupInterval selang pemindaian entri klien yang sudah tidak aktif.
     */
    public record RateLimit(int maxRequests, Duration window, Duration cleanupInterval) {
    }

    /**
     * @param enabled sakelar pembersihan token refresh.
     * @param interval selang antar pembersihan.
     */
    public record RefreshTokenCleanup(boolean enabled, Duration interval) {
    }
}
