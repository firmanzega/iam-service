package id.osprey.iamservice.infrastructure.security;

import id.osprey.iamservice.domain.auth.TokenIssuer;
import id.osprey.iamservice.domain.auth.TokenSettings;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Menerbitkan token JWT dan menghitung sidik jari token refresh.
 *
 * Kenapa token refresh disimpan sebagai hash:
 *      - Database adalah target paling umum dalam kebocoran data. Kalau token
 *        refresh disimpan apa adanya, seluruh token bisa dipakai penyerang
 *        begitu database bocor.
 *      - Menyimpan hanya hash SHA-256 membuat kebocoran database tidak langsung
 *        berarti kebocoran token. SHA-256 cukup untuk ini karena token refresh
 *        dibuat acak dengan entropi tinggi, bukan seperti kata sandi manusia
 *        yang perlu bcrypt.
 */
@Component
public class JwtTokenService implements TokenIssuer {

    private static final String ROLE_CLAIM = "roles";
    private static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtEncoder jwtEncoder;
    private final TokenSettings tokenSettings;

    public JwtTokenService(JwtEncoder jwtEncoder, TokenSettings tokenSettings) {
        this.jwtEncoder = jwtEncoder;
        this.tokenSettings = tokenSettings;
    }

    /**
     * Membuat token akses yang membawa identitas dan role pengguna.
     *
     * @param user pengguna pemilik token
     * @return token akses yang sudah ditandatangani
     */
    @Override
    public String issueAccessToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenSettings.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(tokenSettings.issuer())
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("username", user.getUsername())
                .claim(ROLE_CLAIM, roleNames(user))
                .claim(TOKEN_TYPE_CLAIM, "access")
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Membuat token refresh. Isinya sengaja tidak memuat role, supaya
     * perubahan role tetap berlaku tanpa perlu menunggu token akses kedaluwarsa.
     * Role diambil ulang dari database ketika token ditukar.
     *
     * Claim "jti" wajib ada dan harus unik:
     *      - Tanpa itu, dua token refresh yang terbit pada detik yang sama
     *        untuk pengguna yang sama akan memiliki isi yang persis sama.
     *        Karena hanya hash yang disimpan di database, keduanya bentrok
     *        pada batasan unik token_hash.
     *      - UUID acak membuat setiap token pasti berbeda.
     */
    @Override
    public String issueRefreshToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenSettings.refreshTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(tokenSettings.issuer())
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, "refresh")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Menghitung sidik jari SHA-256 dari token mentah. Ini yang disimpan di
     * database, bukan tokennya sendiri.
     */
    @Override
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 selalu ada di setiap JVM yang memenuhi spesifikasi.
            throw new IllegalStateException("SHA-256 not available on this JVM", ex);
        }
    }

    @Override
    public List<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).sorted().toList();
    }
}
