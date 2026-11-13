package id.osprey.iamservice.application.security;

import id.osprey.iamservice.domain.security.SecurityRejectionStatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji pembentukan jendela waktu dan batas retensi agregat penolakan.
 *
 * Jendela yang tidak dipotong akan membuat satu baris per permintaan, dan itu
 * justru menghilangkan alasan agregat ini dibuat.
 */
@ExtendWith(MockitoExtension.class)
class SecurityRejectionStatsServiceTest {

    @Mock
    SecurityRejectionStatRepository repository;

    @Test
    void record_truncatesWindowToTheMinute() {
        SecurityRejectionStatsService service = new SecurityRejectionStatsService(repository);
        Instant seenAt = Instant.parse("2026-01-01T10:07:42.123Z");

        service.record(seenAt, "GET", "missing_credentials");

        verify(repository).increment(
                Instant.parse("2026-01-01T10:07:00Z"), "GET", "missing_credentials", seenAt);
    }

    @Test
    void deleteOlderThan_subtractsRetention() {
        SecurityRejectionStatsService service = new SecurityRejectionStatsService(repository);
        Instant now = Instant.parse("2026-01-31T10:00:00Z");
        Duration retention = Duration.ofDays(30);

        when(repository.deleteOlderThan(now.minus(retention))).thenReturn(5L);

        long deleted = service.deleteOlderThan(now, retention);

        assertEquals(5L, deleted);
        verify(repository).deleteOlderThan(now.minus(retention));
    }
}
