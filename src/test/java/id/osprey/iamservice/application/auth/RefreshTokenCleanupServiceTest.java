package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.domain.auth.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupServiceTest {

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    RefreshTokenCleanupService cleanupService;

    @Test
    void deleteExpired_usesCallerTimeAsCutoff() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        when(refreshTokenRepository.deleteExpiredBefore(now)).thenReturn(3L);

        long deleted = cleanupService.deleteExpired(now);

        assertEquals(3L, deleted);
        verify(refreshTokenRepository).deleteExpiredBefore(now);
    }
}
