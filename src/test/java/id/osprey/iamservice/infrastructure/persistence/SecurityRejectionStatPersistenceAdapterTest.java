package id.osprey.iamservice.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji jalur increment agregat penolakan dengan JdbcTemplate palsu.
 *
 * Dua cabang yang relevan: baris sudah ada (UPDATE) vs belum ada (INSERT).
 * Pencarian memakai SELECT 1 dengan pemetaan Integer, sehingga query tidak
 * bergantung pada isi baris agregat.
 */
@ExtendWith(MockitoExtension.class)
class SecurityRejectionStatPersistenceAdapterTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    private final Instant seenAt = Instant.parse("2026-01-01T10:07:42Z");
    private final Instant windowStart = Instant.parse("2026-01-01T10:07:00Z");

    @Test
    void increment_existingRow_issuesUpdateNotInsert() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);

        adapter().increment(7L, windowStart, "GET", "invalid_token", seenAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(), any(), any(), any(), any());
        assertTrue(sql.getValue().startsWith("UPDATE"), "baris yang ada harus di-update");
    }

    @Test
    void increment_missingRow_issuesInsert() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        adapter().increment(7L, windowStart, "GET", "invalid_token", seenAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(), any(), any(), any(), any());
        assertTrue(sql.getValue().startsWith("INSERT"), "baris baru harus di-insert");
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
