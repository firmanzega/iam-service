package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.auth.LoginPolicy;
import id.osprey.iamservice.domain.auth.RefreshToken;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import id.osprey.iamservice.domain.auth.TokenIssuer;
import id.osprey.iamservice.domain.auth.TokenSettings;
import id.osprey.iamservice.domain.auth.TokenRevocationService;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final LoginPolicy loginPolicy;
    private final TokenSettings tokenSettings;
    private final RefreshTokenReuseService refreshTokenReuseService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       TokenIssuer tokenIssuer,
                       LoginAttemptService loginAttemptService,
                       AuditService auditService,
                       LoginPolicy loginPolicy,
                       TokenSettings tokenSettings,
                       RefreshTokenReuseService refreshTokenReuseService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.loginPolicy = loginPolicy;
        this.tokenSettings = tokenSettings;
        this.refreshTokenReuseService = refreshTokenReuseService;
    }

    @Transactional
    public TokenPair login(String identifier, String password) {
        Instant now = Instant.now();

        User user = userRepository.findByUsernameOrEmail(identifier, identifier).orElse(null);

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

        auditService.record(user.getId(), AUDIT_LOGIN_SUCCESS, user.getId(), null);

        return issueTokens(user);
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

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenIssuer.hashToken(rawRefreshToken))
                .orElseThrow(() -> new DomainException(DomainException.Reason.INVALID_CREDENTIALS,
                        "Invalid refresh token"));

        if (stored.isRevoked()) {
            if (!stored.isExpired(now)) {
                refreshTokenReuseService.revokeAllAfterReuse(stored.getUser().getId());
            }

            throw new DomainException(DomainException.Reason.INVALID_CREDENTIALS, "Invalid refresh token");
        }

        if (stored.isExpired(now)) {
            throw new DomainException(DomainException.Reason.INVALID_CREDENTIALS, "Invalid refresh token");
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

        auditService.record(user.getId(), AUDIT_TOKEN_REFRESHED, user.getId(), null);

        return issueTokens(user);
    }

    /**
     * Mencabut satu token refresh milik pemanggil.
     *
     * @param userId identitas pemanggil, diambil dari access token
     * @param rawRefreshToken token refresh yang dicabut
     */
    @Transactional
    public void logout(Long userId, String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenIssuer.hashToken(rawRefreshToken))
                .ifPresent(stored -> {
                    if (!stored.getUser().getId().equals(userId)) {
                        throw new DomainException(DomainException.Reason.FORBIDDEN_ACTION,
                                "Refresh token does not belong to the caller");
                    }

                    stored.revoke(Instant.now());
                    refreshTokenRepository.save(stored);
                    auditService.record(userId, AUDIT_LOGOUT, userId, null);
                });
    }

    /**
     * Mencabut seluruh token refresh milik seorang pengguna. Dipakai saat
     * akun dinonaktifkan, kata sandinya diganti atau direset, dan role-nya
     * diubah, supaya sesi tidak bisa diperpanjang dengan token lama.
     */
    @Override
    @Transactional
    public long revokeAllTokens(Long userId) {
        long revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        if (revoked > 0) {
            log.debug("Revoked {} refresh tokens for user {}", revoked, userId);
        }
        return revoked;
    }

    private TokenPair issueTokens(User user) {
        String accessToken = tokenIssuer.issueAccessToken(user);
        String refreshToken = tokenIssuer.issueRefreshToken(user);

        Instant expiresAt = Instant.now().plus(tokenSettings.refreshTokenTtl());
        refreshTokenRepository.save(
                new RefreshToken(user, tokenIssuer.hashToken(refreshToken), expiresAt));

        return new TokenPair(accessToken, refreshToken,
                tokenSettings.accessTokenTtl().toSeconds(), tokenIssuer.roleNames(user));
    }

    private DomainException invalidCredentials() {
        return new DomainException(DomainException.Reason.INVALID_CREDENTIALS, "Invalid credentials");
    }
}
