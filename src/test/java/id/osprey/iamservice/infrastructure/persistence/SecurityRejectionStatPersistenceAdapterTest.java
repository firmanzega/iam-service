package id.osprey.iamservice.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji jalur increment agregat penolakan dengan JdbcTemplate palsu.
 *
 * Dua cabang yang relevan: baris sudah ada (UPDATE) vs belum ada (INSERT).
 * RowMapper ikut dijalankan lewat jawaban stub queryForObject, termasuk
 * dua cabang last_seen_at (NULL dan terisi), karena data lama boleh
 * memiliki last_seen_at NULL. count pada baris hasil SELECT tidak
 * memengaruhi increment, sehingga stub yang berhasil tanpa exception sudah
 * membuktikan mapper dieksekusi.
 */
@ExtendWith(MockitoExtension.class)
class SecurityRejectionStatPersistenceAdapterTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    private final Instant seenAt = Instant.parse("2026-01-01T10:07:42Z");
    private final Instant windowStart = Instant.parse("2026-01-01T10:07:00Z");

    @Test
    void increment_existingRow_issuesUpdateNotInsert() {
        stubFindExisting(3, seenAt);

        adapter().increment(7L, windowStart, "GET", "invalid_token", seenAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object.class), any(Object.class), any(Object.class), any(Object.class), any(Object.class));
        assertTrue(sql.getValue().startsWith("UPDATE"), "baris yang ada harus di-update");
    }

    @Test
    void increment_missingRow_issuesInsert() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        adapter().increment(7L, windowStart, "GET", "invalid_token", seenAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object.class), any(Object.class), any(Object.class), any(Object.class), any(Object.class));
        assertTrue(sql.getValue().startsWith("INSERT"), "baris baru harus di-insert");
    }

    @Test
    void increment_mapsRowWithNullLastSeenAt() {
        stubFindExisting(5, null);

        adapter().increment(7L, windowStart, "GET", "invalid_token", seenAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object.class), any(Object.class), any(Object.class), any(Object.class), any(Object.class));
        assertTrue(sql.getValue().startsWith("UPDATE"));
    }

    @Test
    void increment_mapsRowWithPresentLastSeenAt() {
        stubFindExisting(41, Instant.parse("2026-01-01T10:06:30Z"));

        adapter().increment(7L, windowStart, "POST", "missing_credentials", seenAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object.class), any(Object.class), any(Object.class), any(Object.class), any(Object.class));
        assertTrue(sql.getValue().startsWith("UPDATE"));
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

    @SuppressWarnings("unchecked")
    private void stubFindExisting(int count, Instant lastSeenAt) {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    try {
                        when(resultSet.getInt("rejection_count")).thenReturn(count);
                        lenient().when(resultSet.getTimestamp("last_seen_at"))
                                .thenReturn(lastSeenAt == null ? null : Timestamp.from(lastSeenAt));
                        return mapper.mapRow(resultSet, 0);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
    }
}
