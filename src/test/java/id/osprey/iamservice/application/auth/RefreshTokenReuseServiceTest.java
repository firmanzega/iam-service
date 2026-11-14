package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import id.osprey.iamservice.domain.auth.SessionRepository;
import id.osprey.iamservice.domain.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji penanganan bukti pemakaian ulang token refresh tanpa database.
 *
 * Yang dibuktikan: pencabutan memakai satu waktu yang sama untuk seluruh token,
 * dan jejak audit mencatat berapa token yang ikut dicabut. Angka itu yang
 * dipakai operator untuk menilai apakah kejadiannya wajar.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenReuseServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    SessionRepository sessionRepository;

    @Mock
    AuditService auditService;

    @InjectMocks
    RefreshTokenReuseService reuseService;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @Test
    void revokeAllAfterReuse_revokesEveryTokenAndRecordsAudit() {
        when(refreshTokenRepository.revokeAllForUser(eq(7L), eq(TENANT_ID), any(Instant.class))).thenReturn(3L);

        long revoked = reuseService.revokeAllAfterReuse(7L);

        assertEquals(3L, revoked);
        verify(refreshTokenRepository).revokeAllForUser(eq(7L), eq(TENANT_ID), any(Instant.class));
        verify(sessionRepository).revokeAllForUser(eq(7L), eq(TENANT_ID), any(Instant.class));
        verify(auditService).record(eq(7L), eq("REFRESH_TOKEN_REUSE"), eq(7L),
                contains("3 token aktif dicabut"));
    }

    @Test
    void revokeAllAfterReuse_withoutActiveTokens_stillRecordsAudit() {
        when(refreshTokenRepository.revokeAllForUser(eq(7L), eq(TENANT_ID), any(Instant.class))).thenReturn(0L);

        long revoked = reuseService.revokeAllAfterReuse(7L);

        assertEquals(0L, revoked);
        verify(auditService).record(eq(7L), eq("REFRESH_TOKEN_REUSE"), eq(7L),
                contains("0 token aktif dicabut"));
    }
}
