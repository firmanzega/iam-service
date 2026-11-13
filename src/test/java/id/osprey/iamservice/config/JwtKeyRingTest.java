package id.osprey.iamservice.config;

import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtKeyRingTest {

    private static final String CURRENT_SECRET = "kunci-aktif-yang-cukup-panjang-untuk-hs256-1234";
    private static final String PREVIOUS_SECRET = "kunci-lama-yang-juga-cukup-panjang-untuk-hs256-5678";
    private static final String UNKNOWN_SECRET = "kunci-asing-yang-cukup-panjang-untuk-hs256-9012";

    private final SecretKey currentKey = JwtTestTokens.key(CURRENT_SECRET);
    private final SecretKey previousKey = JwtTestTokens.key(PREVIOUS_SECRET);
    private final SecretKey unknownKey = JwtTestTokens.key(UNKNOWN_SECRET);

    @Test
    void currentKeyId_isDerivedFromKeyAndStable() {
        JwtKeyRing ring = ring(CURRENT_SECRET, null);

        assertEquals(JwtKeyRing.keyId(currentKey), ring.currentKeyId());
        assertEquals(16, ring.currentKeyId().length(), "kid sepanjang 8 byte dalam heksadesimal");
        assertEquals(ring.currentKeyId(), ring(CURRENT_SECRET, null).currentKeyId());
    }

    @Test
    void currentKeyId_differsBetweenKeys() {
        assertNotEquals(JwtKeyRing.keyId(currentKey), JwtKeyRing.keyId(previousKey));
    }

    @Test
    void tokenSignedWithCurrentKey_isAccepted() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(currentKey, ring.currentKeyId());

        Jwt jwt = ring.decoder().decode(token);

        assertEquals("42", jwt.getSubject());
    }

    @Test
    void tokenSignedWithPreviousKeyAndPreviousKid_isAccepted() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(previousKey, JwtKeyRing.keyId(previousKey));

        Jwt jwt = ring.decoder().decode(token);

        assertEquals("42", jwt.getSubject());
    }

    @Test
    void tokenWithUnknownKid_isRejected() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(currentKey, "kid-tidak-dikenal");

        assertThrows(BadJwtException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void tokenSignedWithPreviousKeyButClaimingCurrentKid_isRejected() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(previousKey, ring.currentKeyId());

        // kid hanya memilih kunci; tanda tangan tetap harus cocok dengan kunci
        // yang dipilih. Token ini menunjuk kunci aktif tetapi ditandatangani
        // kunci lama, jadi harus ditolak.
        assertThrows(JwtException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void tokenWithoutKidSignedWithCurrentKey_isAccepted() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);

        Jwt jwt = ring.decoder().decode(JwtTestTokens.accessToken(currentKey, null));

        assertEquals("42", jwt.getSubject());
    }

    @Test
    void tokenWithoutKidSignedWithPreviousKey_isAcceptedDuringRotation() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);

        Jwt jwt = ring.decoder().decode(JwtTestTokens.accessToken(previousKey, null));

        assertEquals("42", jwt.getSubject());
    }

    @Test
    void tokenWithoutKidSignedWithPreviousKey_isRejectedWithoutPreviousKey() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, null);
        String token = JwtTestTokens.accessToken(previousKey, null);

        assertThrows(JwtException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void tokenWithPreviousKid_isRejectedWithoutPreviousKey() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, null);
        String token = JwtTestTokens.accessToken(previousKey, JwtKeyRing.keyId(previousKey));

        assertThrows(BadJwtException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void tokenSignedWithUnknownKeyEvenWithoutKid_isRejected() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(unknownKey, null);

        assertThrows(JwtException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void tokenFromForeignIssuer_isRejected() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(
                currentKey, ring.currentKeyId(), "sistem-lain", Instant.now().plusSeconds(300));

        assertThrows(JwtException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void expiredToken_isRejectedEvenWithCorrectKey() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(
                currentKey, ring.currentKeyId(), JwtTestTokens.ISSUER, Instant.now().minusSeconds(60));

        assertThrows(JwtException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void expiredTokenWithoutKid_keepsValidationFailureInsteadOfTryingPreviousKey() throws JOSEException {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);
        String token = JwtTestTokens.accessToken(
                currentKey, null, JwtTestTokens.ISSUER, Instant.now().minusSeconds(60));

        // Tanda tangan sudah sah, jadi kegagalan yang benar adalah masa
        // berlaku, bukan tanda tangan kunci sebelumnya.
        assertThrows(JwtValidationException.class, () -> ring.decoder().decode(token));
    }

    @Test
    void malformedToken_isRejected() {
        JwtKeyRing ring = ring(CURRENT_SECRET, PREVIOUS_SECRET);

        assertThrows(BadJwtException.class, () -> ring.decoder().decode("bukan-token-jwt"));
    }

    @Test
    void shortCurrentSecret_isRejectedWithPropertyName() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ring("pendek", null));

        assertTrue(error.getMessage().contains("iam.jwt.secret"), error.getMessage());
    }

    @Test
    void shortPreviousSecret_isRejectedWithPropertyName() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ring(CURRENT_SECRET, "pendek"));

        assertTrue(error.getMessage().contains("iam.jwt.previous-secret"), error.getMessage());
    }

    @Test
    void identicalPreviousSecret_isTreatedAsAbsent() throws JOSEException {
        // Kunci yang sama tidak perlu didaftarkan dua kali. Token tetap diterima
        // lewat kunci aktif, dan kid lama tidak bisa dipakai karena memang tidak
        // ada kunci kedua.
        JwtKeyRing ring = ring(CURRENT_SECRET, CURRENT_SECRET);

        Jwt jwt = ring.decoder().decode(JwtTestTokens.accessToken(currentKey, ring.currentKeyId()));

        assertEquals("42", jwt.getSubject());
    }

    private JwtKeyRing ring(String secret, String previousSecret) {
        return new JwtKeyRing(properties(secret, previousSecret));
    }

    private IamProperties properties(String secret, String previousSecret) {
        return new IamProperties(
                new IamProperties.Jwt(secret, JwtTestTokens.ISSUER,
                        Duration.ofMinutes(15), Duration.ofDays(7), previousSecret),
                new IamProperties.Cors(List.of("http://localhost:3000")),
                new IamProperties.Login(5, Duration.ofMinutes(15)),
                new IamProperties.RateLimit(100, Duration.ofMinutes(1), Duration.ofMinutes(10)),
                new IamProperties.RefreshTokenCleanup(true, Duration.ofHours(1)));
    }
}
