package id.osprey.iamservice.config;

import id.osprey.iamservice.TestcontainersConfiguration;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Membuktikan jembatan tracing benar-benar terpasang, bukan hanya ada di
 * daftar dependensi.
 *
 * Sampling dinaikkan khusus di test ini. Di konfigurasi utama sampling bernilai
 * nol, sehingga tidak ada rentang yang dibuat dan tidak ada percobaan ekspor
 * ke collector yang belum tentu ada.
 */
@SpringBootTest(properties = "management.tracing.sampling.probability=1.0")
@Import(TestcontainersConfiguration.class)
class TracingConfigurationTest {

    @Autowired
    private Tracer tracer;

    @Test
    void samplingEnabled_createsSpanWithTraceId() {
        Span span = tracer.nextSpan().name("uji-tracing").start();

        try {
            assertNotNull(span.context().traceId());
            assertFalse(span.context().traceId().isBlank());
        } finally {
            span.end();
        }
    }
}
