package id.osprey.iamservice.application.tenant;

import id.osprey.iamservice.domain.tenant.Tenant;

/**
 * Result of an idempotent tenant provisioning call.
 *
 * @param tenant  the tenant row that exists after the call
 * @param created true only when this call inserted the row
 */
public record ProvisionedTenant(Tenant tenant, boolean created) {
}
