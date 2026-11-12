package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.auth.LoginPolicy;
import id.osprey.iamservice.domain.auth.RefreshToken;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import id.osprey.iamservice.domain.auth.TokenIssuer;
import id.osprey.iamservice.domain.auth.TokenSettings;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji alur autentikasi pada level use case, tanpa lapisan HTTP dan database.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Rahasia123";
    private static final String HASH = "HASH";

    @Mock
    UserRepository userRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    TokenIssuer tokenIssuer;

    @Mock
    LoginAttemptService loginAttemptService;

    @Mock
    AuditService auditService;

    @Spy
    TokenSettings tokenSettings = new TokenSettings(
            "iam-service", Duration.ofMinutes(15), Duration.ofDays(7));

    @Spy
    LoginPolicy loginPolicy = new LoginPolicy(5, Duration.ofMinutes(15));

    @InjectMocks
    AuthService authService;

    private User user(boolean active, boolean locked) {
        Instant lockedUntil = locked ? Instant.now().plusSeconds(900) : null;

        return new User(1L, "budi", "budi@mail.com", HASH, active, 0, lockedUntil,
                null, null, 0L, Set.of(new Role(2L, "USER")));
    }

    private void stubTokenIssuance() {
        when(tokenIssuer.issueAccessToken(any())).thenReturn("access-token");
        when(tokenIssuer.issueRefreshToken(any())).thenReturn("refresh-token");
        when(tokenIssuer.hashToken("refresh-token")).thenReturn("hash");
        when(tokenIssuer.roleNames(any())).thenReturn(List.of("USER"));
    }

    @Test
    void login_validCredentials_returnsTokenPair() {
        when(userRepository.findByUsernameOrEmail("budi", "budi")).thenReturn(Optional.of(user(true, false)));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubTokenIssuance();

        TokenPair result = authService.login("budi", PASSWORD);

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(900L, result.expiresIn());
        assertEquals(List.of("USER"), result.roles());
    }

    @Test
    void login_unknownUser_throwsUnauthorizedWithoutRevealingUserExistence() {
        when(userRepository.findByUsernameOrEmail("entah", "entah")).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> authService.login("entah", PASSWORD));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void login_wrongPassword_recordsFailureAndThrowsUnauthorized() {
        when(userRepository.findByUsernameOrEmail("budi", "budi")).thenReturn(Optional.of(user(true, false)));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        DomainException ex = assertThrows(DomainException.class, () -> authService.login("budi", PASSWORD));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        verify(loginAttemptService).recordFailedLogin(1L);
    }

    @Test
    void login_lockedAccount_throwsLocked() {
        when(userRepository.findByUsernameOrEmail("budi", "budi")).thenReturn(Optional.of(user(true, true)));

        DomainException ex = assertThrows(DomainException.class, () -> authService.login("budi", PASSWORD));

        assertEquals(DomainException.Reason.ACCOUNT_LOCKED, ex.getReason());
        verify(loginAttemptService, never()).recordFailedLogin(anyLong());
    }

    @Test
    void login_inactiveAccount_throwsForbidden() {
        when(userRepository.findByUsernameOrEmail("budi", "budi")).thenReturn(Optional.of(user(false, false)));

        DomainException ex = assertThrows(DomainException.class, () -> authService.login("budi", PASSWORD));

        assertEquals(DomainException.Reason.ACCOUNT_INACTIVE, ex.getReason());
    }

    @Test
    void login_success_clearsFailedLoginCounter() {
        User user = user(true, false);
        user.registerFailedLogin(10, Duration.ofMinutes(15), Instant.now());

        when(userRepository.findByUsernameOrEmail("budi", "budi")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubTokenIssuance();

        authService.login("budi", PASSWORD);

        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void refresh_unknownToken_throwsUnauthorized() {
        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> authService.refresh("token"));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
    }

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        RefreshToken stored = new RefreshToken(1L, user(true, false), "hash",
                Instant.now().plusSeconds(600), Instant.now(), Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        DomainException ex = assertThrows(DomainException.class, () -> authService.refresh("token"));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        RefreshToken stored = new RefreshToken(1L, user(true, false), "hash",
                Instant.now().minusSeconds(10), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        DomainException ex = assertThrows(DomainException.class, () -> authService.refresh("token"));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
    }

    @Test
    void refresh_validToken_rotatesTokenAndRevokesTheOldOne() {
        User user = user(true, false);
        RefreshToken stored = new RefreshToken(1L, user, "hash", Instant.now().plusSeconds(600), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(tokenIssuer.issueAccessToken(any())).thenReturn("access-token-baru");
        when(tokenIssuer.issueRefreshToken(any())).thenReturn("refresh-token-baru");
        when(tokenIssuer.hashToken("refresh-token-baru")).thenReturn("hash-baru");
        when(tokenIssuer.roleNames(any())).thenReturn(List.of("USER"));

        TokenPair result = authService.refresh("token");

        assertEquals("access-token-baru", result.accessToken());
        assertEquals("refresh-token-baru", result.refreshToken());
        assertTrue(stored.isRevoked());
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logout_tokenOfAnotherUser_throwsForbidden() {
        User other = new User(2L, "lain", "lain@mail.com", HASH, true, 0, null,
                null, null, 0L, Set.of());
        RefreshToken stored = new RefreshToken(1L, other, "hash", Instant.now().plusSeconds(600), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        DomainException ex = assertThrows(DomainException.class, () -> authService.logout(1L, "token"));

        assertEquals(DomainException.Reason.FORBIDDEN_ACTION, ex.getReason());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logout_ownToken_revokesIt() {
        User user = user(true, false);
        RefreshToken stored = new RefreshToken(1L, user, "hash", Instant.now().plusSeconds(600), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        authService.logout(1L, "token");

        assertTrue(stored.isRevoked());
        verify(refreshTokenRepository).save(stored);
        verify(auditService).record(eq(1L), eq("LOGOUT"), eq(1L), eq(null));
    }

    @Test
    void revokeAllTokens_delegatesToRepository() {
        when(refreshTokenRepository.revokeAllForUser(1L)).thenReturn(2L);

        authService.revokeAllTokens(1L);

        verify(refreshTokenRepository).revokeAllForUser(1L);
    }
}
