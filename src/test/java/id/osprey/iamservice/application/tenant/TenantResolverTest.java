package id.osprey.iamservice.application.tenant;

import id.osprey.iamservice.domain.tenant.Tenant;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

/**
 * Menguji keputusan TenantResolver: satu-satunya sumber kebenaran untuk
 * apakah sebuah permintaan dianggap milik tenant tertentu.
 *
 * Tabel kebenaran yang dibuktikan di sini:
 *      - Header kosong/tidak ada -> konteks dibersihkan.
 *      - Organisasi tidak ditemukan atau tidak aktif -> konteks dibersihkan.
 *      - Organisasi aktif -> konteks berisi id internal.
 *
 * Perilaku "organisasi tidak ditemukan lalu diam-diam memakai tenant
 * default" adalah cacat yang dilaporkan di BUG-TENANT-001; resolver
 * sendiri sengaja tidak menebak-nebak tenant.
 */
@ExtendWith(MockitoExtension.class)
class TenantResolverTest {

    @Mock
    TenantRepository tenantRepository;

    private TenantResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantResolver(tenantRepository);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void resolve_setsTenantId_whenOrganizationExistsAndActive() {
        Tenant tenant = new Tenant(7L, "QA Tenant", "org-qa", true, Instant.parse("2026-01-01T00:00:00Z"));
        doReturn(Optional.of(tenant)).when(tenantRepository).findByOrganizationId("org-qa");

        resolver.resolve("org-qa");

        assertEquals(7L, TenantContext.getTenantId());
    }

    @Test
    void resolve_clearsContext_whenHeaderIsBlank() {
        TenantContext.setTenantId(99L);

        resolver.resolve("   ");

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void resolve_clearsContext_whenHeaderIsNull() {
        TenantContext.setTenantId(99L);

        resolver.resolve(null);

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void resolve_clearsContext_whenOrganizationNotFound() {
        doReturn(Optional.empty()).when(tenantRepository).findByOrganizationId("org-hilan");

        resolver.resolve("org-hilan");

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void resolve_clearsContext_whenTenantIsInactive() {
        Tenant inactive = new Tenant(8L, "QA Tenant", "org-qa-inactive", false, Instant.parse("2026-01-01T00:00:00Z"));
        doReturn(Optional.of(inactive)).when(tenantRepository).findByOrganizationId("org-qa-inactive");

        resolver.resolve("org-qa-inactive");

        assertNull(TenantContext.getTenantId());
    }
}
