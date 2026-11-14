package id.osprey.iamservice.domain.tenant;

import java.util.Optional;

/**
 * Port untuk resolusi tenant dari identitas organisasi eksternal.
 *
 * Implementasi nyata bertugas mencocokkan nilai header X-Organization-ID
 * ke baris tabel tenants, lalu mengembalikan tenant_id internal.
 */
public interface TenantRepository {

    /**
     * Mencari tenant berdasarkan organization_id yang diekspos eksternal.
     *
     * @param organizationId nilai header X-Organization-ID
     * @return tenant yang cocok, atau kosong bila tidak ada (sengaja
     *         tidak membedakan tidak ada vs tidak valid, untuk mencegah
     *         tebakan ID organisasi)
     */
    Optional<Tenant> findByOrganizationId(String organizationId);

    Tenant save(Tenant tenant);
}
