package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.TestcontainersConfiguration;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Membuktikan deteksi pemakaian ulang token refresh sampai ke database.
 *
 * Skenarionya meniru pencurian token: penyerang memegang token lama, pemilik
 * sah sudah menukarnya. Saat token lama dipakai lagi, seluruh sesi harus mati,
 * termasuk token baru yang dipegang penyerang. Tes unit tidak bisa
 * membuktikan itu karena pencabutan berjalan di transaksi terpisah; hanya
 * database sungguhan yang bisa memperlihatkan hasil commit-nya.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenReuseIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final Long TENANT_ID = 1L;
    private static final String USERNAME = "reusetest-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        removeTestData();

        User user = userRepository.save(new User(TENANT_ID, USERNAME, USERNAME + "@mail.com",
                passwordEncoder.encode(PASSWORD)));
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        removeTestData();
    }

    @Test
    void reusedRefreshToken_revokesAllSessionsAndRecordsAudit() throws Exception {
        String firstToken = login();
        String secondToken = refresh(firstToken);

        // Token pertama sudah dicabut oleh rotasi. Memakainya lagi adalah bukti
        // pemakaian ulang, jadi permintaan ini harus ditolak.
        mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // Token terbaru ikut dicabut, sehingga pencuri tidak bisa melanjutkan
        // sesi walau dia memegang token hasil rotasi.
        mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondToken + "\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals(2, countTokens(), "dua token seharusnya tetap tersimpan sebagai jejak");
        assertEquals(2, countRevokedTokens(), "seluruh token harus ditandai dicabut");
        assertEquals(1, countRevokedSessions(), "sesi pemilik ikut dicabut saat pemakaian ulang terdeteksi");
        // Permintaan kedua memakai token yang dicabut bersama sesinya. Itu bukan
        // bukti pencurian baru, jadi hanya permintaan pertama yang tercatat.
        assertEquals(1, countReuseAudit(), "pencabutan sesi tidak boleh dihitung sebagai pemakaian ulang");
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return extract(result, "refreshToken");
    }

    private String refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return extract(result, "refreshToken");
    }

    private String extract(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private int countTokens() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int countRevokedTokens() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is not null",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int countReuseAudit() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'REFRESH_TOKEN_REUSE' and actor_user_id = ?",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int countRevokedSessions() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sessions where user_id = ? and revoked_at is not null",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    private void removeTestData() {
        jdbcTemplate.update(
                "delete from audit_logs where actor_user_id in (select id from users where username = ?)"
                        + " or target_user_id in (select id from users where username = ?)",
                USERNAME, USERNAME);
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where username = ?)",
                USERNAME);
        jdbcTemplate.update(
                "delete from sessions where user_id in (select id from users where username = ?)",
                USERNAME);
        jdbcTemplate.update(
                "delete from user_roles where user_id in (select id from users where username = ?)",
                USERNAME);
        jdbcTemplate.update("delete from users where username = ?", USERNAME);
    }
}
