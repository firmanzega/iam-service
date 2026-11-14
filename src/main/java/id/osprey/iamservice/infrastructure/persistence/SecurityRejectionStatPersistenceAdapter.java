package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.security.SecurityRejectionStatRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class SecurityRejectionStatPersistenceAdapter implements SecurityRejectionStatRepository {

    private static final String FIND_EXISTING = "SELECT rejection_count, last_seen_at FROM security_rejection_stats WHERE tenant_id = ? AND window_start = ? AND http_method = ? AND reason = ?";
    private static final String INSERT = "INSERT INTO security_rejection_stats (tenant_id, window_start, http_method, reason, rejection_count, last_seen_at) VALUES (?, ?, ?, ?, 1, ?)";
    private static final String UPDATE = "UPDATE security_rejection_stats SET rejection_count = rejection_count + 1, last_seen_at = ? WHERE tenant_id = ? AND window_start = ? AND http_method = ? AND reason = ?";

    private static final RowMapper<RejectionStatRow> ROW_MAPPER = new RowMapper<RejectionStatRow>() {
        @Override
        public RejectionStatRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
            return new RejectionStatRow(
                    rs.getInt("rejection_count"),
                    rs.getTimestamp("last_seen_at") != null
                            ? rs.getTimestamp("last_seen_at").toInstant()
                            : null
            );
        }
    };

    private static class RejectionStatRow {
        private final int rejectionCount;
        private final Instant lastSeenAt;

        RejectionStatRow(int rejectionCount, Instant lastSeenAt) {
            this.rejectionCount = rejectionCount;
            this.lastSeenAt = lastSeenAt;
        }

        int getRejectionCount() { return rejectionCount; }
        Instant getLastSeenAt() { return lastSeenAt; }
    }

    private final JdbcTemplate jdbcTemplate;

    public SecurityRejectionStatPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void increment(Long tenantId, Instant windowStart, String httpMethod, String reason, Instant seenAt) {
        OffsetDateTime seenAtOd = OffsetDateTime.ofInstant(seenAt, ZoneOffset.UTC);
        OffsetDateTime windowStartOd = OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC);

        // Cek record existennya dulu
        RejectionStatRow existing;
        try {
            existing = jdbcTemplate.queryForObject(FIND_EXISTING, ROW_MAPPER, tenantId, windowStartOd, httpMethod, reason);
        } catch (EmptyResultDataAccessException e) {
            existing = null;
        }

        if (existing != null) {
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