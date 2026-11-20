package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.domain.tenant.Tenant;

/**
 * Public tenant shape. The internal tenant_id is deliberately omitted.
 *
 * @param organizationId external organization identifier
 * @param name           display name
 * @param active         whether the tenant is active
 */
public record TenantResponse(String organizationId, String name, boolean active) {

    /**
     * Maps a domain tenant to its public representation.
     *
     * @param tenant tenant to map
     * @return response without the internal identifier
     */
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.getOrganizationId(), tenant.getName(), tenant.isActive());
    }
}
