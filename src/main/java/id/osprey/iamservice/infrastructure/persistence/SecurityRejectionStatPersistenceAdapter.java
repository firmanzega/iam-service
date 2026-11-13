package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.security.SecurityRejectionStatRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Adapter agregat penolakan memakai SQL langsung.
 *
 * Kenapa JdbcTemplate, bukan JPA:
 *      - Yang dibutuhkan adalah satu upsert atomik. Dengan JPA, pola
 *        "cari dulu, lalu tambah atau sisipkan" membuka celah balapan antar
 *        instance, dan kegagalan batasan unik harus ditangani dengan
 *        percobaan ulang.
 *      - Klausa ON CONFLICT milik PostgreSQL menyelesaikan penambahan hitungan
 *        dalam satu perintah, aman untuk banyak instance sekaligus.
 */
@Repository
public class SecurityRejectionStatPersistenceAdapter implements SecurityRejectionStatRepository {

    private static final String UPSERT = """
            insert into security_rejection_stats
                (window_start, http_method, reason, rejection_count, last_seen_at)
            values (?, ?, ?, 1, ?)
            on conflict (window_start, http_method, reason)
            do update set rejection_count = security_rejection_stats.rejection_count + 1,
                          last_seen_at = excluded.last_seen_at
            """;

    private static final String DELETE_OLDER_THAN =
            "delete from security_rejection_stats where window_start < ?";

    private final JdbcTemplate jdbcTemplate;

    public SecurityRejectionStatPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void increment(Instant windowStart, String httpMethod, String reason, Instant seenAt) {
        jdbcTemplate.update(UPSERT,
                OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC),
                httpMethod,
                reason,
                OffsetDateTime.ofInstant(seenAt, ZoneOffset.UTC));
    }

    @Override
    public long deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update(DELETE_OLDER_THAN, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
    }
}
