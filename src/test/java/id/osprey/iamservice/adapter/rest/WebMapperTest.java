package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.config.TokenClaims;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji penerjemah tipe web ke tipe domain.
 *
 * Klaim sesi dibaca dari token yang dikendalikan penandatangan, tetapi
 * nilainya bisa saja tidak berbentuk UUID, misalnya token lama atau token yang
 * diterbitkan versi sebelumnya. Kesalahan membaca klaim seperti itu tidak boleh
 * membuat permintaan gagal; cukup dianggap tidak ada sesi.
 */
class WebMapperTest {

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Jwt token(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .build();
    }

    @Test
    void actorId_withoutToken_isNull() {
        assertNull(WebMapper.actorId(null));
    }

    @Test
    void actorId_readsSubjectAsNumber() {
        assertEquals(42L, WebMapper.actorId(token("42")));
    }

    @Test
    void sessionId_withoutToken_isNull() {
        assertNull(WebMapper.sessionId(null));
    }

    @Test
    void sessionId_withoutClaim_isNull() {
        assertNull(WebMapper.sessionId(token("42")));
    }

    @Test
    void sessionId_withBlankClaim_isNull() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(TokenClaims.SESSION_ID, "   ")
                .build();

        assertNull(WebMapper.sessionId(jwt));
    }

    @Test
    void sessionId_withMalformedValue_isNull() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(TokenClaims.SESSION_ID, "bukan-uuid")
                .build();

        assertNull(WebMapper.sessionId(jwt));
    }

    @Test
    void sessionId_withValidValue_isParsed() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(TokenClaims.SESSION_ID, SESSION_ID.toString())
                .build();

        assertEquals(SESSION_ID, WebMapper.sessionId(jwt));
    }

    @Test
    void tenantId_withoutToken_isNull() {
        assertNull(WebMapper.tenantId(null));
    }

    @Test
    void tenantId_withoutClaim_isNull() {
        assertNull(WebMapper.tenantId(token("42")));
    }

    @Test
    void tenantId_withBlankClaim_isNull() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(TokenClaims.TENANT_ID, "   ")
                .build();

        assertNull(WebMapper.tenantId(jwt));
    }

    @Test
    void tenantId_withMalformedValue_isNull() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(TokenClaims.TENANT_ID, "bukan-angka")
                .build();

        assertNull(WebMapper.tenantId(jwt));
    }

    @Test
    void tenantId_withValidValue_isParsed() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(TokenClaims.TENANT_ID, "7")
                .build();

        assertEquals(7L, WebMapper.tenantId(jwt));
    }

    @Test
    void toRequest_mapsPageSizeAndSort() {
        PaginationRequest request = WebMapper.toRequest(
                PageRequest.of(2, 25, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("username"))));

        assertEquals(2, request.page());
        assertEquals(25, request.size());
        assertEquals(2, request.sort().size());
        assertEquals("createdAt", request.sort().get(0).property());
        assertTrue(request.sort().get(0).descending());
        assertEquals("username", request.sort().get(1).property());
        assertFalse(request.sort().get(1).descending());
    }

    @Test
    void toRequest_withoutSort_returnsEmptySort() {
        PaginationRequest request = WebMapper.toRequest(PageRequest.of(0, 10));

        assertTrue(request.sort().isEmpty());
    }
}
