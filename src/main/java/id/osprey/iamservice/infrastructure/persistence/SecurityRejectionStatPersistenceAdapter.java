package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.security.SecurityRejectionStatRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class SecurityRejectionStatPersistenceAdapter implements SecurityRejectionStatRepository {

    // Single-statement upsert. The composite unique created by V11
    // (tenant_id, window_start, http_method, reason) is the arbiter, so
    // concurrent first-writes and repeat increments in the same minute
    // converge on one row without a read-then-write race.
    private static final String UPSERT =
            "INSERT INTO security_rejection_stats"
                    + " (tenant_id, window_start, http_method, reason, rejection_count, last_seen_at)"
                    + " VALUES (?, ?, ?, ?, 1, ?)"
                    + " ON CONFLICT (tenant_id, window_start, http_method, reason)"
                    + " DO UPDATE SET rejection_count = security_rejection_stats.rejection_count + 1,"
                    + " last_seen_at = EXCLUDED.last_seen_at";

    private final JdbcTemplate jdbcTemplate;

    public SecurityRejectionStatPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void increment(Long tenantId, Instant windowStart, String httpMethod, String reason, Instant seenAt) {
        OffsetDateTime seenAtOd = OffsetDateTime.ofInstant(seenAt, ZoneOffset.UTC);
        OffsetDateTime windowStartOd = OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC);

        jdbcTemplate.update(UPSERT,
                tenantId, windowStartOd, httpMethod, reason, seenAtOd);
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
