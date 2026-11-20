package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param organizationId external identifier used as X-Organization-ID
 * @param name           display name of the tenant
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 100) String organizationId,
        @NotBlank @Size(max = 100) String name) {
}
