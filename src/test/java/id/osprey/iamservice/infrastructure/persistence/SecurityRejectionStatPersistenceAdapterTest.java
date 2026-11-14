package id.osprey.iamservice.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Menguji increment agregat penolakan dengan JdbcTemplate palsu.
 *
 * Sejak V11, increment adalah satu statement upsert (INSERT ... ON CONFLICT
 * ... DO UPDATE) dengan arbiter UNIQUE(tenant_id, window_start, http_method,
 * reason). Test memastikan statement tunggal itu dipakai — tidak ada
 * SELECT-then-INSERT — dan bahwa baris global-DELETE per tenant tetap
 * terpisah dari pembersihan global.
 */
@ExtendWith(MockitoExtension.class)
class SecurityRejectionStatPersistenceAdapterTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    private final Instant seenAt = Instant.parse("2026-01-01T10:07:42Z");
    private final Instant windowStart = Instant.parse("2026-01-01T10:07:00Z");

    @Test
    void increment_emitsSingleUpsertWithTenantScopedArbiter() {
        adapter().increment(7L, windowStart, "GET", "invalid_token", seenAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object.class), any(Object.class),
                any(Object.class), any(Object.class), any(Object.class));

        String captured = sql.getValue();
        assertTrue(captured.startsWith("INSERT"), "harus satu statement INSERT ... ON CONFLICT");
        assertTrue(captured.contains("VALUES (?, ?, ?, ?, 1, ?)"),
                "kejadian pertama harus tertanam rejection_count = 1 di statement");
        assertTrue(captured.contains("ON CONFLICT (tenant_id, window_start, http_method, reason)"),
                "arbiter harus composite tenant-scoped (V11)");
        assertTrue(captured.contains("DO UPDATE"), "cabang konflik harus meng-increment count");
        assertTrue(captured.contains("rejection_count + 1"), "increment berarti +1 per kejadian");
        verify(jdbcTemplate, never()).queryForObject(
                anyString(), eq(Integer.class), any(), any(), any(), any());
    }

    @Test
    void deleteOlderThan_tenantScoped_andGlobal_useSeparateStatements() {
        adapter().deleteOlderThan(9L, Instant.parse("2026-01-01T00:00:00Z"));

        ArgumentCaptor<String> tenantSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(tenantSql.capture(), any(Object.class), any(Object.class));
        assertTrue(tenantSql.getValue().contains("tenant_id = ?"));

        adapter().deleteOlderThan(Instant.parse("2026-01-01T00:00:00Z"));

        ArgumentCaptor<String> globalSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(globalSql.capture(), any(Object.class));
        assertTrue(globalSql.getValue().contains("window_start < ?"));
    }

    private SecurityRejectionStatPersistenceAdapter adapter() {
        return new SecurityRejectionStatPersistenceAdapter(jdbcTemplate);
    }
}
