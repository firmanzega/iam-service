package id.osprey.iamservice.application.tenant;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.application.user.UserService;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.tenant.Tenant;
import id.osprey.iamservice.domain.tenant.TenantRepository;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Use case for Phase 1 tenant provisioning (ADR-0020, ADR-0023).
 *
 * Both operations are idempotent: replaying a call never creates a duplicate
 * tenant or a second ADMIN. Audit records are written with a separate
 * transaction so an audit failure cannot silently disappear with a rollback.
 */
@Service
public class TenantProvisioningService {

    private static final String AUDIT_TENANT_PROVISIONED = "TENANT_PROVISIONED";
    private static final String ADMIN_ROLE = "ADMIN";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuditService auditService;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                     UserRepository userRepository,
                                     UserService userService,
                                     AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.auditService = auditService;
    }

    /**
     * Creates a tenant unless an equal one already exists.
     *
     * @param organizationId external organization identifier
     * @param name           display name, unique across tenants
     * @param actorUserId    OPS actor from the JWT subject
     * @return the tenant and whether it was created by this call
     */
    @Transactional
    public ProvisionedTenant provision(String organizationId, String name, Long actorUserId) {
        Optional<Tenant> existing = tenantRepository.findByOrganizationId(organizationId);
        if (existing.isPresent()) {
            Tenant tenant = existing.get();
            // A different name for the same organization is a real conflict:
            // silently accepting it would leave two names for one identity.
            if (!name.equals(tenant.getName())) {
                throw new DomainException(DomainException.Reason.DUPLICATE_RESOURCE,
                        "organizationId already exists with a different name");
            }
            return new ProvisionedTenant(tenant, false);
        }

        Tenant saved = tenantRepository.save(new Tenant(null, name, organizationId, true, null));
        auditService.recordIndependently(actorUserId, AUDIT_TENANT_PROVISIONED, null,
                "organizationId=" + organizationId + ";name=" + name);
        return new ProvisionedTenant(saved, true);
    }

    /**
     * Bootstraps the first ADMIN of an existing tenant.
     *
     * @param organizationId external organization identifier
     * @param username       login name of the first ADMIN
     * @param email          email of the first ADMIN
     * @param password       raw password, validated by PasswordPolicy
     * @param actorUserId    OPS actor from the JWT subject
     * @return the admin and whether this call created the account
     */
    @Transactional
    public ProvisionedAdmin bootstrapAdmin(String organizationId, String username, String email,
                                           String password, Long actorUserId) {
        Tenant tenant = tenantRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new DomainException(DomainException.Reason.TENANT_NOT_FOUND,
                        "Tenant not found"));

        Optional<User> existing = userRepository.findFirstAdminByTenant(tenant.getId());
        if (existing.isPresent()) {
            return ProvisionedAdmin.from(existing.get(), false);
        }

        User admin = userService.createInTenant(tenant.getId(), username, email, password,
                Set.of(ADMIN_ROLE), actorUserId);
        // record() joins the current transaction on purpose. A REQUIRES_NEW audit
        // write here would wait for the audit-row lock held by this same outer
        // transaction (USER_CREATED + ROLES_REPLACED were already written), and
        // the bootstrap would hang until the database lock timeout. Tenant create
        // still uses recordIndependently because it has no prior audit write.
        auditService.record(actorUserId, AUDIT_TENANT_PROVISIONED, admin.getId(),
                "organizationId=" + organizationId + ";bootstrapAdmin=" + username);
        return ProvisionedAdmin.from(admin, true);
    }

    /**
     * Reads the caller's tenant by internal id.
     *
     * @param id internal tenant id from TenantContext
     * @return the tenant
     */
    @Transactional(readOnly = true)
    public Tenant findById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new DomainException(DomainException.Reason.TENANT_NOT_FOUND,
                        "Tenant not found"));
    }
}
