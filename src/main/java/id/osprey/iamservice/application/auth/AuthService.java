package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.auth.LoginPolicy;
import id.osprey.iamservice.domain.auth.RefreshToken;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import id.osprey.iamservice.domain.auth.Session;
import id.osprey.iamservice.domain.auth.SessionRepository;
import id.osprey.iamservice.domain.auth.TokenIssuer;
import id.osprey.iamservice.domain.auth.TokenSettings;
import id.osprey.iamservice.domain.auth.TokenRevocationService;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.tenant.Tenant;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.tenant.TenantRepository;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use case autentikasi: login, penukaran token refresh, dan logout.
 */
@Service
public class AuthService implements TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String AUDIT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final String AUDIT_LOGIN_FAILED = "LOGIN_FAILED";
    private static final String AUDIT_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    private static final String AUDIT_TOKEN_REFRESHED = "TOKEN_REFRESHED";
    private static final String AUDIT_LOGOUT = "LOGOUT";
    private static final String AUDIT_SESSION_REVOKED = "SESSION_REVOKED";

    private static final String DEFAULT_ORGANIZATION_ID = "org-default";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final LoginPolicy loginPolicy;
    private final TokenSettings tokenSettings;
    private final RefreshTokenReuseService refreshTokenReuseService;
    private final TenantRepository tenantRepository;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       SessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder,
                       TokenIssuer tokenIssuer,
                       LoginAttemptService loginAttemptService,
                       AuditService auditService,
                       LoginPolicy loginPolicy,
                       TokenSettings tokenSettings,
                       RefreshTokenReuseService refreshTokenReuseService,
                       TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.loginPolicy = loginPolicy;
        this.tokenSettings = tokenSettings;
        this.refreshTokenReuseService = refreshTokenReuseService;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Login dan membuka sesi baru untuk perangkat pemanggil.
     *
     * @param deviceLabel label perangkat dari pemanggil, boleh null. Nilainya
     *                    tidak dipercaya sebagai identitas; hanya dipakai
     *                    supaya pemilik akun bisa membedakan sesinya sendiri
     */
    @Transactional
    public TokenPair login(String identifier, String password, String deviceLabel) {
        Instant now = Instant.now();
        TenantContext.setTenantId(currentTenantId());

        User user = userRepository.findByUsernameOrEmail(identifier, identifier,
                TenantContext.getTenantId()).orElse(null);

        if (user == null) {
            // Pesan yang sama dipakai untuk akun yang tidak ada dan untuk kata
            // sandi yang salah. Kalau berbeda, penyerang bisa memakai
            // perbedaan pesan untuk menebak username mana yang terdaftar.
            auditService.recordIndependently(null, AUDIT_LOGIN_FAILED, null,
                    "identifier tidak dikenal: " + identifier);
            throw invalidCredentials();
        }

        if (user.isLocked(now)) {
            auditService.recordIndependently(user.getId(), AUDIT_ACCOUNT_LOCKED, user.getId(), "akun terkunci");
            throw new DomainException(DomainException.Reason.ACCOUNT_LOCKED, "Account is temporarily locked");
        }

        if (!user.isActive()) {
            throw new DomainException(DomainException.Reason.ACCOUNT_INACTIVE, "Account is inactive");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailedLogin(user.getId());
            auditService.recordIndependently(user.getId(), AUDIT_LOGIN_FAILED, user.getId(), "kata sandi salah");
            throw invalidCredentials();
        }

        user.clearFailedLogins();
        userRepository.save(user);

        // Sesi harus milik tenant pemilik pengguna, bukan tenant konteks
        // permintaan. Konteks pada login biasanya kosong (login tidak memakai
        // header) sehingga jatuh ke tenant default; itu membuat sesi pengguna
        // tenant lain tercatat di tenant default.
        Session session = sessionRepository.save(Session.start(user.getId(), user.getTenantId(), deviceLabel, now));

        auditService.record(user.getId(), AUDIT_LOGIN_SUCCESS, user.getId(), null);

        return issueTokens(user, session);
    }

    /**
     * Menukar token refresh menjadi pasangan token baru.
     *
     * Token lama langsung dicabut. Inilah rotasi token refresh: satu token
     * hanya bisa dipakai sekali. Kalau token yang sudah dicabut tetapi belum
     * kedaluwarsa datang lagi, itu dianggap bukti pencurian: seluruh sesi
     * pengguna dicabut lewat transaksi terpisah, lalu permintaan ini tetap
     * dijawab dengan galat yang sama seperti token tidak dikenal. Pesan yang
     * sama sengaja dipakai supaya pemanggil tidak bisa membedakan token yang
     * tidak ada dari token yang dipakai ulang.
     */
    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        TenantContext.setTenantId(currentTenantId());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenIssuer.hashToken(rawRefreshToken))
                .orElseThrow(() -> new DomainException(DomainException.Reason.INVALID_CREDENTIALS,
                        "Invalid refresh token"));

        Session session = stored.getSessionId() == null
                ? null
                : sessionRepository.findById(stored.getSessionId(), TenantContext.getTenantId()).orElse(null);

        if (stored.isRevoked()) {
            // Pencabutan karena logout atau pencabutan sesi bukan bukti
            // pencurian. Yang dianggap pemakaian ulang hanya token yang
            // dicabut oleh rotasi sementara sesinya masih hidup.
            boolean sessionRevoked = session != null && session.isRevoked();
            boolean sessionGone = stored.getSessionId() != null && session == null;

            if (!sessionRevoked && !sessionGone && !stored.isExpired(now)) {
                refreshTokenReuseService.revokeAllAfterReuse(stored.getUser().getId());
            }

            throw invalidRefreshToken();
        }

        if (stored.isExpired(now)) {
            throw invalidRefreshToken();
        }

        if (session != null && session.isRevoked()) {
            throw invalidRefreshToken();
        }

        User user = stored.getUser();

        if (!user.isActive()) {
            throw new DomainException(DomainException.Reason.ACCOUNT_INACTIVE, "Account is inactive");
        }

        if (user.isLocked(now)) {
            throw new DomainException(DomainException.Reason.ACCOUNT_LOCKED, "Account is temporarily locked");
        }

        stored.revoke(now);
        refreshTokenRepository.save(stored);

        if (session == null) {
            // Token yang terbit sebelum sesi perangkat ada tetap bisa ditukar.
            // Sesi baru dibuat sekarang supaya token pengganti punya identitas
            // sesi dan bisa dicabut sendiri nanti.
            session = sessionRepository.save(Session.start(user.getId(), user.getTenantId(), null, now));
        } else {
            session.touch(now);
            sessionRepository.save(session);
        }

        auditService.record(user.getId(), AUDIT_TOKEN_REFRESHED, user.getId(), null);

        return issueTokens(user, session);
    }

    /**
     * Mencabut satu sesi milik pemanggil beserta seluruh token di dalamnya.
     *
     * Logout dari satu perangkat tidak boleh mematikan perangkat lain, jadi
     * yang dicabut adalah sesi yang tokennya dikirim, bukan seluruh sesi
     * pengguna.
     *
     * @param userId identitas pemanggil, diambil dari access token
     * @param rawRefreshToken token refresh milik sesi yang dicabut
     */
    @Transactional
    public void logout(Long userId, String rawRefreshToken) {
        Instant now = Instant.now();

        refreshTokenRepository.findByTokenHash(tokenIssuer.hashToken(rawRefreshToken))
                .ifPresent(stored -> {
                    if (!stored.getUser().getId().equals(userId)) {
                        throw new DomainException(DomainException.Reason.FORBIDDEN_ACTION,
                                "Refresh token does not belong to the caller");
                    }

                    UUID sessionId = stored.getSessionId();
                    if (sessionId != null) {
                        revokeSession(sessionId, now);
                    }

                    stored.revoke(now);
                    refreshTokenRepository.save(stored);
                    auditService.record(userId, AUDIT_LOGOUT, userId, null);
                });
    }

    /**
     * Daftar sesi aktif pemanggil, terbaru lebih dulu.
     *
     * @param userId           identitas pemanggil
     * @param currentSessionId sesi pada token yang sedang dipakai, boleh null
     *                         untuk token lama tanpa klaim sesi
     */
    @Transactional(readOnly = true)
    public List<SessionSummary> listSessions(Long userId, UUID currentSessionId) {
        Instant now = Instant.now();
        var ttl = tokenSettings.refreshTokenTtl();

        return sessionRepository.findActiveByUser(userId, TenantContext.getTenantId(), now.minus(ttl)).stream()
                .map(session -> new SessionSummary(
                        session.getId(),
                        session.getDeviceLabel(),
                        session.getCreatedAt(),
                        session.getLastSeenAt(),
                        session.getLastSeenAt().plus(ttl),
                        session.getId().equals(currentSessionId)))
                .toList();
    }

    /**
     * Mencabut satu sesi milik pemanggil.
     *
     * Sesi yang tidak ada dan sesi milik pengguna lain dijawab sama, yaitu
     * tidak ditemukan. Kalau keduanya dibedakan, pemanggil bisa memakai
     * percobaan itu untuk menebak sesi mana yang ada.
     *
     * @param userId    identitas pemanggil
     * @param sessionId sesi yang dicabut
     */
    @Transactional
    public void revokeSession(Long userId, UUID sessionId) {
        Session session = sessionRepository.findById(sessionId, TenantContext.getTenantId())
                .filter(found -> found.belongsTo(userId))
                .orElseThrow(() -> new DomainException(DomainException.Reason.SESSION_NOT_FOUND,
                        "Session not found"));

        Instant now = Instant.now();
        revokeSession(session, now);
        auditService.record(userId, AUDIT_SESSION_REVOKED, userId, "sesi dicabut: " + sessionId);
    }

    /**
     * Mencabut seluruh token refresh milik seorang pengguna. Dipakai saat
     * akun dinonaktifkan, kata sandinya diganti atau direset, dan role-nya
     * diubah, supaya sesi tidak bisa diperpanjang dengan token lama.
     */
    @Override
    @Transactional
    public long revokeAllTokens(Long userId) {
        Instant now = Instant.now();

        long revoked = refreshTokenRepository.revokeAllForUser(userId, TenantContext.getTenantId(), now);
        sessionRepository.revokeAllForUser(userId, TenantContext.getTenantId(), now);

        if (revoked > 0) {
            log.debug("Revoked {} refresh tokens for user {}", revoked, userId);
        }
        return revoked;
    }

    private void revokeSession(UUID sessionId, Instant now) {
        // Kalau baris sesinya sudah tidak ada, token di dalamnya tetap dicabut.
        // Baris sesi bisa hilang karena pembersihan berkala, dan token yang
        // masih hidup tanpa sesi tidak boleh lolos dari pencabutan.
        sessionRepository.findById(sessionId, TenantContext.getTenantId()).ifPresentOrElse(
                session -> revokeSession(session, now),
                () -> refreshTokenRepository.revokeAllForSession(sessionId, TenantContext.getTenantId(), now));
    }

    private void revokeSession(Session session, Instant now) {
        session.revoke(now);
        sessionRepository.save(session);
        refreshTokenRepository.revokeAllForSession(session.getId(), TenantContext.getTenantId(), now);
    }

    /**
     * Mengembalikan tenantId untuk permintaan saat ini. Login adalah endpoint
     * publik yang tidak memakai header X-Organization-ID, jadi kalau konteks
     * tenant kosong pakai tenant default supaya sesi dan token tetap bisa
     * dibuat dengan tenant yang valid.
     */
    private Long currentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByOrganizationId(DEFAULT_ORGANIZATION_ID)
                .map(Tenant::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant default '" + DEFAULT_ORGANIZATION_ID + "' tidak ditemukan"));
    }

    private DomainException invalidRefreshToken() {
        return new DomainException(DomainException.Reason.INVALID_CREDENTIALS, "Invalid refresh token");
    }

    private TokenPair issueTokens(User user, Session session) {
        String accessToken = tokenIssuer.issueAccessToken(user, session.getId());
        String refreshToken = tokenIssuer.issueRefreshToken(user, session.getId());

        Instant expiresAt = Instant.now().plus(tokenSettings.refreshTokenTtl());
        refreshTokenRepository.save(
                new RefreshToken(user, user.getTenantId(),
                        tokenIssuer.hashToken(refreshToken), session.getId(), expiresAt));

        return new TokenPair(accessToken, refreshToken,
                tokenSettings.accessTokenTtl().toSeconds(), tokenIssuer.roleNames(user));
    }

    private DomainException invalidCredentials() {
        return new DomainException(DomainException.Reason.INVALID_CREDENTIALS, "Invalid credentials");
    }
}
