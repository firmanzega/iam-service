package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.domain.auth.SessionRepository;
import id.osprey.iamservice.domain.auth.TokenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji batas waktu pembersihan sesi tanpa database.
 *
 * Yang dibuktikan hanya satu hal: yang dihapus adalah sesi yang tidak dipakai
 * lebih lama dari masa berlaku token refresh. Kalau batas ini salah, sesi yang
 * masih bisa dilanjutkan ikut terhapus.
 */
@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceTest {

    @Mock
    SessionRepository sessionRepository;

    @Test
    void deleteInactive_usesRefreshTokenLifetimeAsCutoff() {
        Duration ttl = Duration.ofDays(7);
        TokenSettings settings = new TokenSettings("iam-service", Duration.ofMinutes(15), ttl);
        SessionCleanupService service = new SessionCleanupService(sessionRepository, settings);

        Instant now = Instant.parse("2026-01-08T10:00:00Z");
        when(sessionRepository.deleteInactiveBefore(eq(now.minus(ttl)))).thenReturn(3L);

        long deleted = service.deleteInactive(now);

        assertEquals(3L, deleted);
        verify(sessionRepository).deleteInactiveBefore(now.minus(ttl));
    }
}
