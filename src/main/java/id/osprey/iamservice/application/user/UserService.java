package id.osprey.iamservice.application.user;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.application.security.PasswordPolicy;
import id.osprey.iamservice.domain.auth.TokenRevocationService;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.user.UserRepository;
import id.osprey.iamservice.domain.user.UserSortableProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Use case pengelolaan pengguna.
 *
 * Lapisan ini tidak tahu apa-apa soal HTTP maupun JPA. Dia hanya mengenal aturan
 * bisnis dan port yang dideklarasikan domain.
 */
@Service
public class UserService {

    private static final String AUDIT_USER_CREATED = "USER_CREATED";
    private static final String AUDIT_USER_UPDATED = "USER_UPDATED";
    private static final String AUDIT_USER_ACTIVATED = "USER_ACTIVATED";
    private static final String AUDIT_USER_DEACTIVATED = "USER_DEACTIVATED";
    private static final String AUDIT_ROLES_REPLACED = "ROLES_REPLACED";
    private static final String AUDIT_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    private static final String AUDIT_PASSWORD_RESET = "PASSWORD_RESET";
    private static final String AUDIT_TOKENS_REVOKED = "TOKENS_REVOKED";

    /**
     * Roles that must never be assigned through the public API. OPS is a
     * cross-tenant capability (audit-chain verification) that must only be
     * granted via the operator path described in docs/07-DEPLOYMENT.md
     * section 13.1, never via self-service by a tenant ADMIN. Custom roles
     * created through POST /v1/roles remain assignable — only OPS is barred
     * here. Keep in sync with docs/04-API-SPEC.yaml.
     */
    private static final Set<String> RESTRICTED_ROLES = Set.of("OPS");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;
    private final TokenRevocationService tokenRevocationService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordPolicy passwordPolicy,
                       AuditService auditService,
                       TokenRevocationService tokenRevocationService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.tokenRevocationService = tokenRevocationService;
        this.passwordEncoder = passwordEncoder;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // Fail-closed, konsisten dengan AuthService: tanpa konteks, use
            // case tidak boleh menebak tenant. HTTP path selalu memiliki
            // konteks karena filter menolak permintaan tanpa header.
            throw new IllegalStateException(
                    "TenantContext kosong di UserService; TenantHeaderFilter harus menolak permintaan lebih dulu");
        }
        return tenantId;
    }

    /**
     * Mencari pengguna.
     *
     * Properti pengurutan diperiksa di sini, bukan di controller, karena aturan
     * "properti mana yang boleh diurutkan" adalah aturan bisnis. Dengan begitu
     * aturan ini tetap berlaku untuk pemanggil lain, bukan cuma lewat HTTP.
     */
    @Transactional(readOnly = true)
    public PageResult<User> search(String query, PaginationRequest request) {
        validateSort(request);

        return (query == null || query.isBlank())
                ? userRepository.findAll(request)
                : userRepository.search(query.trim(), request);
    }

    /**
     * Menolak pengurutan berdasarkan properti di luar daftar yang diizinkan.
     */
    private void validateSort(PaginationRequest request) {
        List<String> rejected = request.sort().stream()
                .map(spec -> spec.property())
                .filter(property -> !UserSortableProperties.ALLOWED.contains(property))
                .distinct()
                .toList();

        if (!rejected.isEmpty()) {
            throw new DomainException(DomainException.Reason.INVALID_SORT_PROPERTY,
                    "Properties not allowed for sorting: " + rejected
                            + ". Allowed: " + UserSortableProperties.ALLOWED);
        }
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public User create(String username, String email, String rawPassword, Long actorUserId) {
        passwordPolicy.validate(rawPassword);

        String hash = passwordEncoder.encode(rawPassword);
        User user = userRepository.save(new User(currentTenantId(), username, email, hash));

        auditService.record(actorUserId, AUDIT_USER_CREATED, user.getId(), user.getUsername());

        return user;
    }

    /**
     * Creates a user in an explicitly supplied tenant.
     *
     * Provisioning bootstraps an ADMIN in a tenant that is not the caller's
     * tenant, so it cannot reuse create(): that method reads TenantContext and
     * replacing the context for a request would leak across concurrent calls.
     * Role assignment is bundled here so the new account is usable in one
     * transaction. Restricted roles stay forbidden, as in replaceRoles().
     */
    @Transactional
    public User createInTenant(Long tenantId, String username, String email, String rawPassword,
                               Set<String> roleNames, Long actorUserId) {
        passwordPolicy.validate(rawPassword);

        if (userRepository.findByUsernameOrEmail(username, email, tenantId).isPresent()) {
            throw new DomainException(DomainException.Reason.DUPLICATE_RESOURCE,
                    "Username or email already used in this tenant");
        }

        Set<String> restricted = new TreeSet<>(roleNames);
        restricted.retainAll(RESTRICTED_ROLES);
        if (!restricted.isEmpty()) {
            throw new DomainException(DomainException.Reason.ROLE_NOT_ASSIGNABLE,
                    "Roles cannot be assigned via the API: " + String.join(", ", restricted));
        }

        Set<Role> roles = roleRepository.findByNameIn(roleNames);
        if (roles.size() != roleNames.size()) {
            throw new DomainException(DomainException.Reason.UNKNOWN_ROLE, "Unknown roles: " + roleNames);
        }

        User user = new User(tenantId, username, email, passwordEncoder.encode(rawPassword));
        user.replaceRoles(roles);
        User saved = userRepository.save(user);

        auditService.record(actorUserId, AUDIT_USER_CREATED, saved.getId(), saved.getUsername());
        auditService.record(actorUserId, AUDIT_ROLES_REPLACED, saved.getId(),
                String.join(",", roles.stream().map(Role::getName).sorted().toList()));
        return saved;
    }

    @Transactional
    public User update(Long id, String username, String email, Long version, Long actorUserId) {
        User user = findOrThrow(id);
        checkVersion(user, version);

        user.changeUsername(username);
        user.changeEmail(email);

        User saved = userRepository.save(user);
        auditService.record(actorUserId, AUDIT_USER_UPDATED, id, saved.getUsername());

        return saved;
    }

    @Transactional
    public User patch(Long id, String username, String email, Long version, Long actorUserId) {
        User user = findOrThrow(id);
        checkVersion(user, version);

        if (username != null) {
            user.changeUsername(username);
        }

        if (email != null) {
            user.changeEmail(email);
        }

        User saved = userRepository.save(user);
        auditService.record(actorUserId, AUDIT_USER_UPDATED, id, saved.getUsername());

        return saved;
    }

    @Transactional
    public void deactivate(Long id, Long actorUserId) {
        User user = findOrThrow(id);
        user.deactivate();
        userRepository.save(user);

        // Akun yang dinonaktifkan tidak boleh bisa memakai token yang masih hidup.
        long revoked = tokenRevocationService.revokeAllTokens(id);

        auditService.record(actorUserId, AUDIT_TOKENS_REVOKED, id, revokedTokensDetail(revoked));
        auditService.record(actorUserId, AUDIT_USER_DEACTIVATED, id, user.getUsername());
    }

    @Transactional
    public void activate(Long id, Long actorUserId) {
        User user = findOrThrow(id);
        user.activate();
        user.clearFailedLogins();
        userRepository.save(user);

        auditService.record(actorUserId, AUDIT_USER_ACTIVATED, id, user.getUsername());
    }

    /**
     * Mengganti kata sandi oleh pemiliknya sendiri.
     *
     * Setelah kata sandi berubah, seluruh token refresh milik pengguna dicabut.
     * Alasannya: bila kata sandi diganti karena ada kecurigaan password bocor,
     * sesi lama harus ikut mati. Kalau tidak, penyerang yang sudah punya token
     * masih bisa masuk.
     */
    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword, Long actorUserId) {
        User user = findOrThrow(id);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new DomainException(DomainException.Reason.CURRENT_PASSWORD_MISMATCH,
                    "Current password is incorrect");
        }

        passwordPolicy.validate(newPassword);

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new DomainException(DomainException.Reason.INVALID_PASSWORD,
                    "New password must be different from the current one");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        long revoked = tokenRevocationService.revokeAllTokens(id);

        auditService.record(actorUserId, AUDIT_TOKENS_REVOKED, id, revokedTokensDetail(revoked));
        auditService.record(actorUserId, AUDIT_PASSWORD_CHANGED, id, user.getUsername());
    }

    /**
     * Menetapkan kata sandi baru oleh administrator.
     *
     * Berbeda dari ganti kata sandi sendiri, di sini administrator tidak perlu
     * mengetahui kata sandi lama pengguna. Bedanya, seluruh sesi pengguna
     * langsung dicabut.
     */
    @Transactional
    public void resetPassword(Long id, String newPassword, Long actorUserId) {
        User user = findOrThrow(id);

        passwordPolicy.validate(newPassword);

        user.changePassword(passwordEncoder.encode(newPassword));
        user.clearFailedLogins();
        userRepository.save(user);

        long revoked = tokenRevocationService.revokeAllTokens(id);

        auditService.record(actorUserId, AUDIT_TOKENS_REVOKED, id, revokedTokensDetail(revoked));
        auditService.record(actorUserId, AUDIT_PASSWORD_RESET, id, user.getUsername());
    }

    /**
     * Mengganti seluruh role pengguna.
     *
     * Role yang tidak dikirim akan dilepas. Role yang tidak dikenal ditolak.
     * Role yang dikenal tetapi tidak boleh di-assign lewat jalur API ditolak.
     * Pengguna (termasuk ADMIN) tidak boleh mengubah role miliknya sendiri.
     */
    @Transactional
    public User replaceRoles(Long id, Set<String> roleNames, Long version, Long actorUserId) {
        if (id.equals(actorUserId)) {
            // Self-assignment even of an assignable role defeats the audit
            // boundary: a compromised ADMIN session could silently lock out or
            // elevate itself. Another ADMIN (or the operator path) must act.
            throw new DomainException(DomainException.Reason.SELF_ROLE_CHANGE_NOT_ALLOWED,
                    "Users cannot change their own roles via the API");
        }

        Set<String> requested = new TreeSet<>(roleNames);
        Set<String> restricted = new TreeSet<>(requested);
        restricted.retainAll(RESTRICTED_ROLES);

        if (!restricted.isEmpty()) {
            throw new DomainException(DomainException.Reason.ROLE_NOT_ASSIGNABLE,
                    "Roles cannot be assigned via the API: " + String.join(", ", restricted));
        }

        User user = findOrThrow(id);
        checkVersion(user, version);

        Set<Role> roles = roleRepository.findByNameIn(roleNames);

        if (roles.size() != roleNames.size()) {
            Set<String> found = roles.stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
            Set<String> missing = new TreeSet<>(roleNames);
            missing.removeAll(found);

            throw new DomainException(DomainException.Reason.UNKNOWN_ROLE, "Unknown roles: " + missing);
        }

        user.replaceRoles(roles);
        User saved = userRepository.save(user);

        // Role ikut tertanam di dalam access token. Kalau role dicabut, token
        // lama masih membawa role lama sampai kedaluwarsa. Mencabut token
        // refresh membuat pengguna harus login ulang supaya role terbaru berlaku.
        long revoked = tokenRevocationService.revokeAllTokens(id);

        auditService.record(actorUserId, AUDIT_TOKENS_REVOKED, id, revokedTokensDetail(revoked));
        auditService.record(actorUserId, AUDIT_ROLES_REPLACED, id, String.join(",", saved.getRoles().stream()
                .map(Role::getName).sorted().toList()));

        return saved;
    }

    /**
     * Rincian jumlah token pada catatan TOKENS_REVOKED.
     *
     * Kenapa dicatat per kejadian pemicu: kejadian seperti PASSWORD_CHANGED
     * menjelaskan alasannya, tetapi tidak menjelaskan berapa sesi yang ikut
     * mati. Jumlah itu yang dipakai saat menelusuri apakah pencabutan benar
     * mengenai sesi yang dicurigai.
     */
    private String revokedTokensDetail(long revoked) {
        return "token refresh dicabut: " + revoked;
    }

    private User findOrThrow(Long id) {
        // Tenant-scoped read: /v1/users/* requires X-Organization-ID (401 bila
        // hilang), sehingga konteks selalu terisi di jalur HTTP. Pemanggil
        // non-HTTP tanpa konteks mendapat himpunan kosong, bukan data tenant
        // lain — gagal tertutup, bukan gagal terbuka.
        Long tenantId = TenantContext.getTenantId();
        return userRepository.findById(id, tenantId)
                .orElseThrow(() -> new DomainException(DomainException.Reason.USER_NOT_FOUND, "User not found"));
    }

    private void checkVersion(User user, Long version) {
        if (version == null || user.getVersion() != version) {
            throw new DomainException(DomainException.Reason.CONCURRENT_MODIFICATION,
                    "The resource was modified by someone else. Reload and try again");
        }
    }
}
