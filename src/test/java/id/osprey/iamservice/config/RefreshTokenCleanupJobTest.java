package id.osprey.iamservice.config;

import id.osprey.iamservice.application.auth.RefreshTokenCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Memastikan penjadwal hanya dibuat saat diaktifkan, dan memastikan method
 * terjadwal benar-benar memanggil use case pembersihan.
 *
 * Kenapa memakai ApplicationContextRunner, bukan @SpringBootTest: yang diuji
 * hanya syarat pembuatan bean dan satu pemanggilan method. Menyalakan seluruh
 * konteks aplikasi untuk itu memperlambat tes tanpa menambah bukti.
 */
class RefreshTokenCleanupJobTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RefreshTokenCleanupService.class, () -> mock(RefreshTokenCleanupService.class))
            .withUserConfiguration(RefreshTokenCleanupJob.class);

    @Test
    void jobIsRegisteredByDefault() {
        contextRunner.run(context ->
                assertEquals(1, context.getBeansOfType(RefreshTokenCleanupJob.class).size()));
    }

    @Test
    void jobIsNotRegisteredWhenDisabled() {
        contextRunner.withPropertyValues("iam.refresh-token-cleanup.enabled=false")
                .run(context ->
                        assertTrue(context.getBeansOfType(RefreshTokenCleanupJob.class).isEmpty()));
    }

    @Test
    void deleteExpiredTokens_delegatesToCleanupService() {
        RefreshTokenCleanupService cleanupService = mock(RefreshTokenCleanupService.class);
        when(cleanupService.deleteExpired(any(Instant.class))).thenReturn(2L);
        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(cleanupService);

        job.deleteExpiredTokens();

        verify(cleanupService).deleteExpired(any(Instant.class));
    }
}
