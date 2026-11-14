package id.osprey.iamservice.application.tenant;

import id.osprey.iamservice.domain.tenant.Tenant;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.tenant.TenantRepository;
import org.springframework.stereotype.Component;

/**
 * Meng-resolve header X-Organization-ID menjadi tenant_id internal, lalu
 * menyimpannya di TenantContext untuk masa berlaku satu permintaan.
 *
 * Mengembalikan Resolution supaya pemanggil (TenantHeaderFilter) bisa
 * membedakan header yang tidak ada (MISSING) dari organisasi yang tidak
 * dikenal atau tidak aktif (UNKNOWN). Keduanya tidak boleh jatuh ke
 * tenant default; fallback diam-diam adalah BUG-TENANT-001.
 */
@Component
public class TenantResolver {

    public enum Resolution {
        RESOLVED,
        MISSING,
        UNKNOWN
    }

    private final TenantRepository tenantRepository;

    public TenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Resolution resolve(String organizationId) {
        TenantContext.clear();
        if (organizationId == null || organizationId.isBlank()) {
            return Resolution.MISSING;
        }

        Tenant tenant = tenantRepository.findByOrganizationId(organizationId).orElse(null);
        if (tenant != null && tenant.isActive()) {
            TenantContext.setTenantId(tenant.getId());
            return Resolution.RESOLVED;
        }
        return Resolution.UNKNOWN;
    }
}
