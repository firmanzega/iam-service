package id.osprey.iamservice.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Sepasang kunci penanda tangan token: kunci aktif dan kunci sebelumnya.
 *
 * Kenapa perlu dua kunci:
 *      - Mengganti satu-satunya kunci berarti seluruh token yang sudah terbit
 *        langsung tidak sah. Semua pengguna terpaksa login ulang hanya karena
 *        kunci diganti.
 *      - Dengan kunci sebelumnya tetap dikenali, kunci aktif bisa diganti
 *        tanpa memutus sesi. Token lama diterima sampai masa berlakunya habis,
 *        lalu kunci sebelumnya boleh dihapus.
 *
 * Kenapa token membawa "kid":
 *      - Tanpa penanda, decoder hanya bisa mencoba kunci satu per satu. Itu
 *        masih mungkin untuk dua kunci, tetapi menjadi ambigu saat kuncinya
 *        lebih dari dua dan menyulitkan penelusuran token mana yang memakai
 *        kunci mana.
 *      - "kid" diturunkan dari hash kunci, bukan diisi manual. Nilainya stabil
 *        tanpa konfigurasi tambahan, dan tidak membocorkan kunci itu sendiri.
 *
 * Token yang terbit sebelum "kid" ada tetap diterima selama kunci sebelumnya
 * masih dikonfigurasi. Tanpa kunci sebelumnya, token seperti itu ditolak.
 *
 * Kelas ini final karena konstruktornya sengaja menolak konfigurasi tidak sah
 * dengan melempar exception. Subclass yang punya finalizer bisa membuat objek
 * setengah jadi tetap hidup; final menutup jalur itu.
 */
@Component
public final class JwtKeyRing {

    /**
     * Panjang "kid" dalam byte. Delapan byte cukup untuk membedakan kunci yang
     * beredar bersamaan; nilai ini bukan pengenal keamanan, hanya pemilih kunci.
     */
    private static final int KEY_ID_BYTES = 8;

    private final String currentKeyId;
    private final String previousKeyId;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtKeyRing(IamProperties properties) {
        IamProperties.Jwt jwt = properties.jwt();

        SecretKey currentKey = secretKey(jwt.secret(), "iam.jwt.secret");
        this.currentKeyId = keyId(currentKey);

        SecretKey earlierKey = jwt.previousSecret() == null
                ? null
                : secretKey(jwt.previousSecret(), "iam.jwt.previous-secret");

        // Kunci yang isinya sama tidak perlu didaftarkan dua kali. Tanpa
        // pemeriksaan ini, dua kid identik membuat pemilihan kunci ambigu.
        if (earlierKey != null && keyId(earlierKey).equals(currentKeyId)) {
            earlierKey = null;
        }

        this.previousKeyId = earlierKey == null ? null : keyId(earlierKey);

        NimbusJwtDecoder currentDecoder = decoderFor(currentKey, jwt.issuer());
        NimbusJwtDecoder earlierDecoder = earlierKey == null
                ? null
                : decoderFor(earlierKey, jwt.issuer());

        this.encoder = encoderFor(currentKey, currentKeyId);
        this.decoder = token -> decode(token, currentDecoder, earlierDecoder);
    }

    /** Penanda kunci yang sedang dipakai untuk menerbitkan token. */
    public String currentKeyId() {
        return currentKeyId;
    }

    /** Encoder yang selalu memakai kunci aktif. */
    public JwtEncoder encoder() {
        return encoder;
    }

    /** Decoder yang menerima kunci aktif dan, bila ada, kunci sebelumnya. */
    public JwtDecoder decoder() {
        return decoder;
    }

    private Jwt decode(String token, NimbusJwtDecoder current, NimbusJwtDecoder earlier) {
        String keyId = headerKeyId(token);

        if (keyId == null) {
            // Token lama terbit sebelum kid dipasang. Kunci aktif dicoba lebih
            // dulu karena itu kasus yang paling sering, lalu kunci sebelumnya.
            try {
                return current.decode(token);
            } catch (JwtValidationException ex) {
                // Tanda tangan sudah cocok; yang gagal adalah klaim, misalnya
                // masa berlaku. Mencoba kunci lain tidak akan mengubah hasil.
                throw ex;
            } catch (JwtException ex) {
                if (earlier == null) {
                    throw ex;
                }
                return earlier.decode(token);
            }
        }

        if (keyId.equals(currentKeyId)) {
            return current.decode(token);
        }

        if (earlier != null && keyId.equals(previousKeyId)) {
            return earlier.decode(token);
        }

        throw new BadJwtException("Token memakai kid yang tidak dikenal");
    }

    /**
     * Membaca kid dari header tanpa memverifikasi tanda tangan. Nilai ini hanya
     * dipakai untuk memilih kunci; tanda tangan tetap diperiksa oleh decoder
     * yang dipilih, dan penerbit serta masa berlaku tetap divalidasi.
     */
    private static String headerKeyId(String token) {
        try {
            String keyId = SignedJWT.parse(token).getHeader().getKeyID();
            return keyId == null || keyId.isBlank() ? null : keyId;
        } catch (ParseException ex) {
            throw new BadJwtException("Token tidak bisa dibaca", ex);
        }
    }

    /**
     * Encoder memilih kunci berdasarkan "kid" pada header. Karena itu kunci
     * harus dipublikasikan sebagai JWK yang membawa "kid" yang sama. JWK
     * seperti ini juga memastikan token yang terbit selalu ditandatangani
     * kunci aktif, bukan kunci lain yang kebetulan tersedia.
     */
    private static JwtEncoder encoderFor(SecretKey key, String keyId) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.HS256)
                .build();

        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    }

    private static NimbusJwtDecoder decoderFor(SecretKey key, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Validasi penerbit mencegah token yang diterbitkan sistem lain
        // diterima di sini, meskipun tanda tangannya sah.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Kunci HMAC harus panjangnya minimal 256 bit. String yang lebih pendek
     * ditolak, karena kunci lemah lebih berbahaya daripada aplikasi gagal start.
     */
    private static SecretKey secretKey(String value, String propertyName) {
        byte[] secret = value.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    propertyName + " minimal 32 byte, sekarang hanya " + secret.length + " byte");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    /**
     * Diturunkan dari isi kunci, bukan dari konfigurasi, supaya penanda yang
     * sama selalu muncul untuk kunci yang sama. Terbuka pada tingkat paket
     * agar pengujian bisa membandingkan kid yang diharapkan tanpa menyalin
     * rumusnya.
     */
    static String keyId(SecretKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getEncoded());
            return HexFormat.of().formatHex(Arrays.copyOf(hash, KEY_ID_BYTES));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 selalu ada di setiap JVM yang memenuhi spesifikasi.
            throw new IllegalStateException("SHA-256 tidak tersedia", ex);
        }
    }
}
