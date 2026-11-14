package id.osprey.iamservice.application.tenant;

import id.osprey.iamservice.domain.tenant.Tenant;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.tenant.TenantRepository;
import org.springframework.stereotype.Component;

/**
 * Meng-resolve header X-Organization-ID menjadi tenant_id internal, lalu
 * menyimpannya di TenantContext untuk masa berlaku satu permintaan.
 *
 * Kalau header tidak ada atau organisasi tidak ditemukan, TenantContext
 * dikosongkan. Pemeriksaan keabsahan header dilakukan oleh TenantHeaderFilter
 * (400 bila hilang di endpoint terisolasi); resolver hanya mengatur konteks.
 */
@Component
public class TenantResolver {

    private final TenantRepository tenantRepository;

    public TenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public void resolve(String organizationId) {
        TenantContext.clear();
        if (organizationId == null || organizationId.isBlank()) {
            return;
        }

        Tenant tenant = tenantRepository.findByOrganizationId(organizationId).orElse(null);
        if (tenant != null && tenant.isActive()) {
            TenantContext.setTenantId(tenant.getId());
        }
    }
}
