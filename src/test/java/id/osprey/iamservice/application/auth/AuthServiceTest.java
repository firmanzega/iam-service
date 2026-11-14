package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.auth.LoginPolicy;
import id.osprey.iamservice.domain.auth.RefreshToken;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import id.osprey.iamservice.domain.auth.Session;
import id.osprey.iamservice.domain.auth.SessionRepository;
import id.osprey.iamservice.domain.auth.TokenIssuer;
import id.osprey.iamservice.domain.auth.TokenSettings;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji alur autentikasi pada level use case, tanpa lapisan HTTP dan database.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Rahasia123";
    private static final String HASH = "HASH";
    private static final Long TENANT_ID = 1L;
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    UserRepository userRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    SessionRepository sessionRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    TokenIssuer tokenIssuer;

    @Mock
    LoginAttemptService loginAttemptService;

    @Mock
    AuditService auditService;

    @Mock
    RefreshTokenReuseService refreshTokenReuseService;

    @Spy
    TokenSettings tokenSettings = new TokenSettings(
            "iam-service", Duration.ofMinutes(15), Duration.ofDays(7));

    @Spy
    LoginPolicy loginPolicy = new LoginPolicy(5, Duration.ofMinutes(15));

    @InjectMocks
    AuthService authService;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(TENANT_ID);
    }

    private User user(boolean active, boolean locked) {
        Instant lockedUntil = locked ? Instant.now().plusSeconds(900) : null;

        return new User(1L, TENANT_ID, "budi", "budi@mail.com", HASH, active, 0, lockedUntil,
                null, null, 0L, Set.of(new Role(2L, "USER")));
    }

    private Session session(UUID id, boolean revoked) {
        Instant now = Instant.now();
        return new Session(id, 1L, "laptop", now, now, revoked ? now : null);
    }

    private void stubTokenIssuance() {
        when(tokenIssuer.issueAccessToken(any(), any())).thenReturn("access-token");
        when(tokenIssuer.issueRefreshToken(any(), any())).thenReturn("refresh-token");
        when(tokenIssuer.hashToken("refresh-token")).thenReturn("hash");
        when(tokenIssuer.roleNames(any())).thenReturn(List.of("USER"));
    }

    private void stubSessionSave() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void login_withoutTenantContext_throwsIllegalState() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class,
                () -> authService.login("budi", PASSWORD, "laptop"));

        verify(userRepository, never()).findByUsernameOrEmail(any(), any(), any());
    }

    @Test
    void refresh_prefersStoredTokenTenant_overCurrentContext() {
        // Konteks permintaan menunjuk tenant 99, tetapi token tersimpan
        // milik tenant pengguna (1). ADR-0018 poin 6: penukaran menyesuaikan
        // tenant tersimpan, sehingga lookup sesi memakai tenant 1.
        TenantContext.setTenantId(99L);
        User user = user(true, false);
        RefreshToken stored = new RefreshToken(1L, user, TENANT_ID, "hash", SESSION_ID,
                Instant.now().plusSeconds(600), null, Instant.now());
        Session session = session(SESSION_ID, false);

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(sessionRepository.findById(SESSION_ID, TENANT_ID)).thenReturn(Optional.of(session));
        stubSessionSave();
        stubTokenIssuance();

        authService.refresh("token");

        verify(sessionRepository).findById(SESSION_ID, TENANT_ID);
        assertEquals(TENANT_ID, TenantContext.getTenantId());
    }

    @Test
    void login_validCredentials_returnsTokenPair() {
        when(userRepository.findByUsernameOrEmail(eq("budi"), eq("budi"), eq(1L)))
                .thenReturn(Optional.of(user(true, false)));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubSessionSave();
        stubTokenIssuance();

        TokenPair result = authService.login("budi", PASSWORD, "laptop kantor");

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(900L, result.expiresIn());
        assertEquals(List.of("USER"), result.roles());

        ArgumentCaptor<Session> savedSession = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(savedSession.capture());
        assertEquals(1L, savedSession.getValue().getUserId());
        assertEquals("laptop kantor", savedSession.getValue().getDeviceLabel());
        assertFalse(savedSession.getValue().isRevoked());
    }

    @Test
    void login_withoutDeviceLabel_storesSessionWithoutLabel() {
        when(userRepository.findByUsernameOrEmail(eq("budi"), eq("budi"), eq(1L)))
                .thenReturn(Optional.of(user(true, false)));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubSessionSave();
        stubTokenIssuance();

        authService.login("budi", PASSWORD, null);

        ArgumentCaptor<Session> savedSession = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(savedSession.capture());
        assertNull(savedSession.getValue().getDeviceLabel());
    }

    @Test
    void login_unknownUser_throwsUnauthorizedWithoutRevealingUserExistence() {
        when(userRepository.findByUsernameOrEmail(eq("entah"), eq("entah"), eq(1L))).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> authService.login("entah", PASSWORD, null));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void login_wrongPassword_recordsFailureAndThrowsUnauthorized() {
        when(userRepository.findByUsernameOrEmail(eq("budi"), eq("budi"), eq(1L)))
                .thenReturn(Optional.of(user(true, false)));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        DomainException ex = assertThrows(DomainException.class,
                () -> authService.login("budi", PASSWORD, null));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        verify(loginAttemptService).recordFailedLogin(1L);
    }

    @Test
    void login_lockedAccount_throwsLocked() {
        when(userRepository.findByUsernameOrEmail(eq("budi"), eq("budi"), eq(1L)))
                .thenReturn(Optional.of(user(true, true)));

        DomainException ex = assertThrows(DomainException.class,
                () -> authService.login("budi", PASSWORD, null));

        assertEquals(DomainException.Reason.ACCOUNT_LOCKED, ex.getReason());
        verify(loginAttemptService, never()).recordFailedLogin(anyLong());
    }

    @Test
    void login_inactiveAccount_throwsForbidden() {
        when(userRepository.findByUsernameOrEmail(eq("budi"), eq("budi"), eq(1L)))
                .thenReturn(Optional.of(user(false, false)));

        DomainException ex = assertThrows(DomainException.class,
                () -> authService.login("budi", PASSWORD, null));

        assertEquals(DomainException.Reason.ACCOUNT_INACTIVE, ex.getReason());
    }

    @Test
    void login_success_clearsFailedLoginCounter() {
        User user = user(true, false);
        user.registerFailedLogin(10, Duration.ofMinutes(15), Instant.now());

        when(userRepository.findByUsernameOrEmail(eq("budi"), eq("budi"), eq(1L))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubSessionSave();
        stubTokenIssuance();

        authService.login("budi", PASSWORD, null);

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
    void refresh_revokedTokenWithActiveSession_isTreatedAsReuse() {
        RefreshToken stored = new RefreshToken(1L, user(true, false), "hash", SESSION_ID,
                Instant.now().plusSeconds(600), Instant.now(), Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(sessionRepository.findById(eq(SESSION_ID), eq(1L))).thenReturn(Optional.of(session(SESSION_ID, false)));

        DomainException ex = assertThrows(DomainException.class, () -> authService.refresh("token"));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        verify(refreshTokenReuseService).revokeAllAfterReuse(1L);
    }

    @Test
    void refresh_revokedTokenWithRevokedSession_isNotTreatedAsReuse() {
        RefreshToken stored = new RefreshToken(1L, user(true, false), "hash", SESSION_ID,
                Instant.now().plusSeconds(600), Instant.now(), Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(sessionRepository.findById(eq(SESSION_ID), eq(1L))).thenReturn(Optional.of(session(SESSION_ID, true)));

        DomainException ex = assertThrows(DomainException.class, () -> authService.refresh("token"));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        verify(refreshTokenReuseService, never()).revokeAllAfterReuse(anyLong());
    }

    @Test
    void refresh_revokedAndExpiredToken_doesNotRevokeOtherSessions() {
        RefreshToken stored = new RefreshToken(1L, user(true, false), "hash", null,
                Instant.now().minusSeconds(10), Instant.now().minusSeconds(60), Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        DomainException ex = assertThrows(DomainException.class, () -> authService.refresh("token"));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        verify(refreshTokenReuseService, never()).revokeAllAfterReuse(anyLong());
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        RefreshToken stored = new RefreshToken(1L, user(true, false), "hash", null,
                Instant.now().minusSeconds(10), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        DomainException ex = assertThrows(DomainException.class, () -> authService.refresh("token"));

        assertEquals(DomainException.Reason.INVALID_CREDENTIALS, ex.getReason());
        verify(refreshTokenReuseService, never()).revokeAllAfterReuse(anyLong());
    }

    @Test
    void refresh_validToken_rotatesTokenAndKeepsTheSameSession() {
        User user = user(true, false);
        Session session = session(SESSION_ID, false);
        RefreshToken stored = new RefreshToken(1L, user, "hash", SESSION_ID,
                Instant.now().plusSeconds(600), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(sessionRepository.findById(eq(SESSION_ID), eq(1L))).thenReturn(Optional.of(session));
        when(tokenIssuer.issueAccessToken(any(), any())).thenReturn("access-token-baru");
        when(tokenIssuer.issueRefreshToken(any(), any())).thenReturn("refresh-token-baru");
        when(tokenIssuer.hashToken("refresh-token-baru")).thenReturn("hash-baru");
        when(tokenIssuer.roleNames(any())).thenReturn(List.of("USER"));

        TokenPair result = authService.refresh("token");

        assertEquals("access-token-baru", result.accessToken());
        assertEquals("refresh-token-baru", result.refreshToken());
        assertTrue(stored.isRevoked());

        ArgumentCaptor<UUID> issuedSession = ArgumentCaptor.forClass(UUID.class);
        verify(tokenIssuer).issueAccessToken(any(), issuedSession.capture());
        assertEquals(SESSION_ID, issuedSession.getValue());

        verify(sessionRepository).save(session);
        assertFalse(session.isRevoked());
    }

    @Test
    void refresh_legacyTokenWithoutSession_startsANewSession() {
        User user = user(true, false);
        RefreshToken stored = new RefreshToken(1L, user, "hash", null,
                Instant.now().plusSeconds(600), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        stubSessionSave();
        when(tokenIssuer.issueAccessToken(any(), any())).thenReturn("access-token-baru");
        when(tokenIssuer.issueRefreshToken(any(), any())).thenReturn("refresh-token-baru");
        when(tokenIssuer.hashToken("refresh-token-baru")).thenReturn("hash-baru");
        when(tokenIssuer.roleNames(any())).thenReturn(List.of("USER"));

        authService.refresh("token");

        ArgumentCaptor<Session> savedSession = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(savedSession.capture());
        assertNull(savedSession.getValue().getDeviceLabel());

        // Satu penyimpanan adalah token lama yang dicabut, satu lagi token
        // pengganti. Yang diperiksa hanya token pengganti, karena token lama
        // memang tidak punya sesi.
        ArgumentCaptor<RefreshToken> savedTokens = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(savedTokens.capture());
        assertTrue(savedTokens.getAllValues().stream()
                        .anyMatch(token -> savedSession.getValue().getId().equals(token.getSessionId())),
                "token pengganti harus menunjuk sesi baru");
    }

    @Test
    void logout_tokenOfAnotherUser_throwsForbidden() {
        User other = new User(2L, TENANT_ID, "lain", "lain@mail.com", HASH, true, 0, null,
                null, null, 0L, Set.of());
        RefreshToken stored = new RefreshToken(1L, other, "hash", null,
                Instant.now().plusSeconds(600), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        DomainException ex = assertThrows(DomainException.class, () -> authService.logout(1L, "token"));

        assertEquals(DomainException.Reason.FORBIDDEN_ACTION, ex.getReason());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logout_ownToken_revokesItsWholeSession() {
        User user = user(true, false);
        Session session = session(SESSION_ID, false);
        RefreshToken stored = new RefreshToken(1L, user, "hash", SESSION_ID,
                Instant.now().plusSeconds(600), null, Instant.now());

        when(tokenIssuer.hashToken("token")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(sessionRepository.findById(eq(SESSION_ID), eq(1L))).thenReturn(Optional.of(session));

        authService.logout(1L, "token");

        assertTrue(session.isRevoked());
        assertTrue(stored.isRevoked());
        verify(sessionRepository).save(session);
        verify(refreshTokenRepository).revokeAllForSession(eq(SESSION_ID), anyLong(), any(Instant.class));
        verify(auditService).record(eq(1L), eq("LOGOUT"), eq(1L), eq(null));
    }

    @Test
    void revokeSession_ofAnotherUser_isReportedAsNotFound() {
        Session foreign = new Session(SESSION_ID, 2L, "milik orang lain",
                Instant.now(), Instant.now(), null);

        when(sessionRepository.findById(eq(SESSION_ID), eq(1L))).thenReturn(Optional.of(foreign));

        DomainException ex = assertThrows(DomainException.class,
                () -> authService.revokeSession(1L, SESSION_ID));

        assertEquals(DomainException.Reason.SESSION_NOT_FOUND, ex.getReason());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void revokeSession_ofOwnSession_revokesSessionAndItsTokens() {
        Session session = session(SESSION_ID, false);

        when(sessionRepository.findById(eq(SESSION_ID), eq(1L))).thenReturn(Optional.of(session));

        authService.revokeSession(1L, SESSION_ID);

        assertTrue(session.isRevoked());
        verify(sessionRepository).save(session);
        verify(refreshTokenRepository).revokeAllForSession(eq(SESSION_ID), anyLong(), any(Instant.class));
        verify(auditService).record(eq(1L), eq("SESSION_REVOKED"), eq(1L), any(String.class));
    }

    @Test
    void listSessions_marksOnlyTheCallerSessionAsCurrent() {
        Instant now = Instant.now();
        Session current = new Session(SESSION_ID, 1L, "laptop", now, now, null);
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Session other = new Session(otherId, 1L, "telepon", now, now, null);

        when(sessionRepository.findActiveByUser(eq(1L), anyLong(), any(Instant.class)))
                .thenReturn(List.of(current, other));

        List<SessionSummary> sessions = authService.listSessions(1L, SESSION_ID);

        assertEquals(2, sessions.size());
        assertTrue(sessions.get(0).current());
        assertFalse(sessions.get(1).current());
        assertEquals("laptop", sessions.get(0).deviceLabel());
        assertEquals(SESSION_ID, sessions.get(0).id());
    }

    @Test
    void revokeAllTokens_revokesTokensAndSessions() {
        when(refreshTokenRepository.revokeAllForUser(eq(1L), anyLong(), any(Instant.class))).thenReturn(2L);

        long revoked = authService.revokeAllTokens(1L);

        assertEquals(2L, revoked);
        verify(refreshTokenRepository).revokeAllForUser(eq(1L), anyLong(), any(Instant.class));
        verify(sessionRepository).revokeAllForUser(eq(1L), anyLong(), any(Instant.class));
    }
}
