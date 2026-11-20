package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.adapter.rest.dto.BootstrapAdminRequest;
import id.osprey.iamservice.adapter.rest.dto.CreateTenantRequest;
import id.osprey.iamservice.adapter.rest.dto.TenantResponse;
import id.osprey.iamservice.adapter.rest.dto.UserResponse;
import id.osprey.iamservice.application.tenant.ProvisionedAdmin;
import id.osprey.iamservice.application.tenant.ProvisionedTenant;
import id.osprey.iamservice.application.tenant.TenantProvisioningService;
import id.osprey.iamservice.domain.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for Phase 1 tenant provisioning (ADR-0023).
 *
 * Authorization is enforced in SecurityConfig, not in this controller:
 * provisioning is a deployment-operator capability and must stay visible in
 * one place alongside the other global rules.
 */
@Tag(name = "Tenants", description = "Tenant provisioning (OPS only)")
@RestController
@RequestMapping("/v1/tenants")
public class TenantController {

    private final TenantProvisioningService provisioningService;

    public TenantController(TenantProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    /**
     * Creates a tenant, or returns the existing one on an idempotent replay.
     */
    @Operation(summary = "Provision a tenant", description = "OPS only. Idempotent by organizationId.")
    @PostMapping
    public ResponseEntity<TenantResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CreateTenantRequest request) {
        ProvisionedTenant result = provisioningService.provision(
                request.organizationId(), request.name(), WebMapper.actorId(jwt));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TenantResponse.from(result.tenant()));
    }

    /**
     * Bootstraps the first ADMIN of an existing tenant.
     */
    @Operation(summary = "Bootstrap the first ADMIN of a tenant", description = "OPS only. Idempotent per tenant.")
    @PostMapping("/{organizationId}/bootstrap-admin")
    public ResponseEntity<UserResponse> bootstrapAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String organizationId,
            @Valid @RequestBody BootstrapAdminRequest request) {
        ProvisionedAdmin result = provisioningService.bootstrapAdmin(
                organizationId, request.username(), request.email(), request.password(), WebMapper.actorId(jwt));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        UserResponse body = new UserResponse(result.id(), result.username(), result.email(),
                result.active(), result.version(), result.roles());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Returns the tenant resolved from the caller's organization header.
     */
    @Operation(summary = "Read the caller's current tenant")
    @GetMapping("/current")
    public TenantResponse current() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext is empty on a tenant-aware path");
        }
        return TenantResponse.from(provisioningService.findById(tenantId));
    }
}
