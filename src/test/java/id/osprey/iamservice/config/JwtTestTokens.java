package id.osprey.iamservice.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Pembuat token untuk pengujian key ring dan rotasi kunci.
 *
 * Kenapa tidak memakai JwtTokenService:
 *      - Tes rotasi perlu menerbitkan token dengan kunci yang sudah tidak
 *        aktif, dan dengan header tanpa "kid". JwtTokenService sengaja hanya
 *        bisa memakai kunci aktif dan selalu menulis "kid".
 *      - Membuat token langsung dengan Nimbus membuat tes bisa menentukan
 *        kunci, kid, penerbit, dan masa berlaku secara tepat.
 */
final class JwtTestTokens {

    static final String ISSUER = "iam-service";

    private JwtTestTokens() {
    }

    /** Kunci HMAC dari teks, dengan panjang yang cukup untuk HS256. */
    static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Token akses yang masih berlaku dan ditandatangani dengan kunci tertentu. */
    static String accessToken(SecretKey key, String kid) throws JOSEException {
        return accessToken(key, kid, ISSUER, Instant.now().plusSeconds(300));
    }

    /** Token akses dengan penerbit dan masa berlaku yang bisa ditentukan tes. */
    static String accessToken(SecretKey key, String kid, String issuer, Instant expiresAt)
            throws JOSEException {
        Instant issuedAt = expiresAt.minusSeconds(300);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("42")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("username", "pengguna-uji")
                .claim("roles", List.of("USER"))
                .claim("token_type", "access")
                .build();

        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.HS256);
        if (kid != null) {
            header.keyID(kid);
        }

        SignedJWT jwt = new SignedJWT(header.build(), claims);
        jwt.sign(new MACSigner(key.getEncoded()));

        return jwt.serialize();
    }
}
