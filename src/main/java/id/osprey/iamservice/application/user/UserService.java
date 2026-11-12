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
        User user = userRepository.save(new User(username, email, hash));

        auditService.record(actorUserId, AUDIT_USER_CREATED, user.getId(), user.getUsername());

        return user;
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
     */
    @Transactional
    public User replaceRoles(Long id, Set<String> roleNames, Long version, Long actorUserId) {
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
        return userRepository.findById(id)
                .orElseThrow(() -> new DomainException(DomainException.Reason.USER_NOT_FOUND, "User not found"));
    }

    private void checkVersion(User user, Long version) {
        if (version == null || user.getVersion() != version) {
            throw new DomainException(DomainException.Reason.CONCURRENT_MODIFICATION,
                    "The resource was modified by someone else. Reload and try again");
        }
    }
}
