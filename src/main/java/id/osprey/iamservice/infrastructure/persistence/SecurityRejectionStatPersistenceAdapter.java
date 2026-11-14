package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.security.SecurityRejectionStatRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class SecurityRejectionStatPersistenceAdapter implements SecurityRejectionStatRepository {

    // Existence check only. The aggregate row's values are never read by the
    // increment path (it only needs to know whether the row exists), so the
    // query selects a constant and maps to Integer. Selecting the actual
    // columns forced a RowMapper whose getters stayed unreachable from
    // public behavior, which failed the per-class JaCoCo gate (QA-GATE-001).
    private static final String FIND_EXISTING =
            "SELECT 1 FROM security_rejection_stats"
                    + " WHERE tenant_id = ? AND window_start = ? AND http_method = ? AND reason = ?";
    private static final String INSERT =
            "INSERT INTO security_rejection_stats"
                    + " (tenant_id, window_start, http_method, reason, rejection_count, last_seen_at)"
                    + " VALUES (?, ?, ?, ?, 1, ?)";
    private static final String UPDATE =
            "UPDATE security_rejection_stats"
                    + " SET rejection_count = rejection_count + 1, last_seen_at = ?"
                    + " WHERE tenant_id = ? AND window_start = ? AND http_method = ? AND reason = ?";

    private final JdbcTemplate jdbcTemplate;

    public SecurityRejectionStatPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void increment(Long tenantId, Instant windowStart, String httpMethod, String reason, Instant seenAt) {
        OffsetDateTime seenAtOd = OffsetDateTime.ofInstant(seenAt, ZoneOffset.UTC);
        OffsetDateTime windowStartOd = OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC);

        // Cek record existennya dulu
        boolean existing;
        try {
            jdbcTemplate.queryForObject(FIND_EXISTING, Integer.class, tenantId, windowStartOd, httpMethod, reason);
            existing = true;
        } catch (EmptyResultDataAccessException e) {
            existing = false;
        }

        if (existing) {
            // Record ada, update count dan last_seen_at
            jdbcTemplate.update(UPDATE,
                    seenAtOd, tenantId, windowStartOd, httpMethod, reason);
        } else {
            // Record belum ada, insert baru
            jdbcTemplate.update(INSERT,
                    tenantId, windowStartOd, httpMethod, reason, seenAtOd);
        }
    }

    @Override
    public long deleteOlderThan(Long tenantId, Instant cutoff) {
        OffsetDateTime cutoffOd = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        return jdbcTemplate.update("delete from security_rejection_stats where tenant_id = ? and window_start < ?",
                tenantId, cutoffOd);
    }

    @Override
    public long deleteOlderThan(Instant cutoff) {
        OffsetDateTime cutoffOd = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        return jdbcTemplate.update("delete from security_rejection_stats where window_start < ?",
                cutoffOd);
    }
}
