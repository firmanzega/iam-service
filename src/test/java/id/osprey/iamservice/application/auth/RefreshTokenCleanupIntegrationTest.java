package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.TestcontainersConfiguration;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Memastikan pembersihan benar-benar menghapus baris kedaluwarsa di database,
 * bukan hanya memanggil repository dengan parameter yang benar.
 *
 * Token yang masih berlaku sengaja ikut disisipkan sebagai pembanding. Kalau
 * query pembersihan salah menulis syarat, token yang masih hidup juga akan
 * hilang, dan pengguna akan ter-logout tanpa alasan.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenCleanupIntegrationTest {

    private static final String USERNAME = "cleanup-test";
    private static final Long TENANT_ID = 1L;
    private static final String EXPIRED_HASH = "cleanup-expired-hash";
    private static final String ACTIVE_HASH = "cleanup-active-hash";
    private static final String REVOKED_HASH = "cleanup-revoked-hash";

    @Autowired
    private RefreshTokenCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long userId;

    @BeforeEach
    void setUp() {
        removeTestData();

        User user = userRepository.save(new User(TENANT_ID, USERNAME, USERNAME + "@mail.com",
                passwordEncoder.encode("Rahasia123")));
        userId = user.getId();

        insertToken(EXPIRED_HASH, Instant.now().minus(1, ChronoUnit.DAYS), null);
        insertToken(ACTIVE_HASH, Instant.now().plus(1, ChronoUnit.DAYS), null);
        // Token yang dicabut tetapi belum kedaluwarsa sengaja dipertahankan
        // sebagai bukti untuk deteksi pemakaian ulang di masa depan.
        insertToken(REVOKED_HASH, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now());
    }

    @AfterEach
    void tearDown() {
        removeTestData();
    }

    @Test
    void deleteExpired_removesOnlyExpiredTokens() {
        long deleted = cleanupService.deleteExpired(Instant.now());

        assertEquals(1, deleted, "hanya satu token kedaluwarsa yang seharusnya dihapus");
        assertEquals(0, countByHash(EXPIRED_HASH), "token kedaluwarsa seharusnya sudah dihapus");
        assertEquals(1, countByHash(ACTIVE_HASH), "token yang masih berlaku tidak boleh ikut terhapus");
        assertEquals(1, countByHash(REVOKED_HASH),
                "token yang dicabut tetapi belum kedaluwarsa harus tetap disimpan");
    }

    private void insertToken(String tokenHash, Instant expiresAt, Instant revokedAt) {
        jdbcTemplate.update(
                "insert into refresh_tokens (user_id, token_hash, expires_at, revoked_at, tenant_id)"
                        + " values (?, ?, ?, ?, ?)",
                userId, tokenHash, Timestamp.from(expiresAt),
                revokedAt == null ? null : Timestamp.from(revokedAt), TENANT_ID);
    }

    private int countByHash(String tokenHash) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, tokenHash);
        return count == null ? 0 : count;
    }

    private void removeTestData() {
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where username = ?)",
                USERNAME);
        jdbcTemplate.update("delete from users where username = ?", USERNAME);
    }
}
