package id.osprey.iamservice.domain.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Menguji konstruktor AuditLog: jejak audit memakai beberapa jalan masuk
 * (JPA, service audit, jalur legacy), dan masing-masing tidak boleh
 * kehilangan tenantId atau id baris.
 */
class AuditLogTest {

    @Test
    void legacyFourArgConstructor_setsTenantAndIdToNull_andStampsNow() {
        AuditLog entry = new AuditLog(1L, "LOGIN_SUCCESS", 1L, null);

        assertNull(entry.getId());
        assertNull(entry.getTenantId());
        assertEquals(1L, entry.getActorUserId());
        assertEquals("LOGIN_SUCCESS", entry.getAction());
        assertEquals(1L, entry.getTargetUserId());
        assertNull(entry.getDetails());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    void tenantAwareFiveArgConstructor_carriesTenant() {
        AuditLog entry = new AuditLog(1L, 9L, "TOKENS_REVOKED", 2L, "detail");

        assertNull(entry.getId());
        assertEquals(9L, entry.getTenantId());
        assertEquals(1L, entry.getActorUserId());
        assertEquals("TOKENS_REVOKED", entry.getAction());
        assertEquals(2L, entry.getTargetUserId());
        assertEquals("detail", entry.getDetails());
    }

    @Test
    void sixArgConstructor_withoutTenant_forLegacyRows() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        AuditLog entry = new AuditLog(55L, 1L, "LOGOUT", 1L, "detail", createdAt);

        assertEquals(55L, entry.getId());
        assertNull(entry.getTenantId());
        assertEquals(createdAt, entry.getCreatedAt());
    }

    @Test
    void sevenArgConstructor_isFullMapping() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        AuditLog entry = new AuditLog(55L, 9L, 1L, "SESSION_REVOKED", 3L, "detail lengkap", createdAt);

        assertEquals(55L, entry.getId());
        assertEquals(9L, entry.getTenantId());
        assertEquals(1L, entry.getActorUserId());
        assertEquals("SESSION_REVOKED", entry.getAction());
        assertEquals(3L, entry.getTargetUserId());
        assertEquals("detail lengkap", entry.getDetails());
        assertEquals(createdAt, entry.getCreatedAt());
    }
}
