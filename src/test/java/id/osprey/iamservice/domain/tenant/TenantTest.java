package id.osprey.iamservice.domain.tenant;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji nilai bawaan entitas tenant: satu tenant non-aktif harus terlihat
 * non-aktif oleh resolver, dan nilai pemetaan tidak boleh berubah di domain.
 */
class TenantTest {

    @Test
    void accessors_returnConstructorValues() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Tenant tenant = new Tenant(7L, "QA Tenant", "org-qa", true, createdAt);

        assertEquals(7L, tenant.getId());
        assertEquals("QA Tenant", tenant.getName());
        assertEquals("org-qa", tenant.getOrganizationId());
        assertTrue(tenant.isActive());
        assertEquals(createdAt, tenant.getCreatedAt());
    }

    @Test
    void inactiveTenant_isRecognizedAsInactive() {
        Tenant inactive = new Tenant(8L, "Mati", "org-mati", false, Instant.now());

        assertFalse(inactive.isActive());
    }
}
