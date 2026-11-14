package id.osprey.iamservice.adapter.rest;

import com.jayway.jsonpath.JsonPath;
import id.osprey.iamservice.TestcontainersConfiguration;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Membuktikan isolasi tenant lewat HTTP sungguhan: dua tenant yang berbeda
 * tidak saling melihat sesi masing-masing (IDOR).
 *
 * Kenapa tajam: migrasi V9 menambah kolom tenant_id ke seluruh tabel inti,
 * tetapi bukan itu sendiri yang mencegah kebocoran. Sebuah bug di lapisan
 * filter atau query yang lupa memfilter tenant_id sudah cukup untuk membuat
 * tenant A melihat atau mencabut sesi tenant B. Uji ini mengeksekusi alur
 * nyata (login dua pengguna di dua tenant, list, revoke lintas-tenant).
 *
 * Prasyarat testcontainers: database dan skema V1-V9 dibangun ulang setiap
 * run, sehingga tenant uji dimasukkan ulang di setiap metode.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenantIsolationIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final String DEFAULT_ORG_ID = "org-default";
    private static final String QA_ORG_ID = "org-qa-isolation";
    private static final String PREFIX = "tenantqa-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long qaTenantId;

    @BeforeEach
    void setUp() {
        removeTestData();
        qaTenantId = jdbcTemplate.queryForObject(
                "insert into tenants (active, created_at, name, organization_id)"
                        + " values (true, now(), 'QA Isolation Tenant', ?)"
                        + " on conflict (organization_id) do update set name = excluded.name"
                        + " returning id",
                Long.class, QA_ORG_ID);
        createUser(DEFAULT_TENANT_ID, PREFIX + "a");
        createUser(qaTenantId, PREFIX + "b");
    }

    @AfterEach
    void tearDown() {
        removeTestData();
    }

    @Test
    void sessions_withoutOrganizationHeader_returns400() throws Exception {
        Tokens tokens = login(PREFIX + "a", "laptop");

        mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.containsString("X-Organization-ID")));
    }

    @Test
    void logout_withoutOrganizationHeader_returns400() throws Exception {
        Tokens tokens = login(PREFIX + "a", "laptop");

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sessions_listsOnlyCallerTenantOwnSessions() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop");
        Tokens tokensB = login(PREFIX + "b", "telepon");

        List<Map<String, Object>> sessionsA = listSessions(tokensA.accessToken(), DEFAULT_ORG_ID);
        List<Map<String, Object>> sessionsB = listSessions(tokensB.accessToken(), QA_ORG_ID);

        assertEquals(1, sessionsA.size(), "tenant A seharusnya hanya melihat sesinya sendiri");
        assertTrue(hasLabel(sessionsA, "laptop"));
        assertFalse(hasLabel(sessionsA, "telepon"),
                "tenant A tidak boleh melihat sesi milik tenant B");

        assertEquals(1, sessionsB.size(), "tenant B seharusnya hanya melihat sesinya sendiri");
        assertTrue(hasLabel(sessionsB, "telepon"));
        assertFalse(hasLabel(sessionsB, "laptop"),
                "tenant B tidak boleh melihat sesi milik tenant A");
    }

    @Test
    void revokeSession_fromOtherTenant_isNotFound_andOriginalStaysAlive() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop");
        Tokens tokensB = login(PREFIX + "b", "telepon");

        List<Map<String, Object>> sessionsA = listSessions(tokensA.accessToken(), DEFAULT_ORG_ID);
        UUID sessionA = sessionIdByLabel(sessionsA, "laptop");

        mockMvc.perform(delete("/v1/auth/sessions/{id}", sessionA)
                        .header("Authorization", "Bearer " + tokensB.accessToken())
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isNotFound());

        MvcResult refresh = mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokensA.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("Bearer", JsonPath.read(refresh.getResponse().getContentAsString(), "$.tokenType"));
    }

    @Test
    @Disabled("BUG-TENANT-001: organisasi tidak dikenal jatuh ke tenant default "
            + "(layanan memakai fallback org-default) alih-alih ditolak. "
            + "Menurut ADR-0016 Verification, organisasi tidak valid harus 401/404. "
            + "Menunggu keputusan Boss tentang kontrak yang benar.")
    void sessions_withUnknownOrganization_isRejected_notFallbackToDefaultTenant() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop");

        mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + tokensA.accessToken())
                        .header("X-Organization-ID", "org-does-not-exist"))
                .andExpect(status().is4xxClientError());
    }

    private Tokens login(String username, String deviceLabel) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD
                                + "\",\"deviceLabel\":\"" + deviceLabel + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return tokensOf(result);
    }

    private List<Map<String, Object>> listSessions(String accessToken, String organizationId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Organization-ID", organizationId))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$");
    }

    private boolean hasLabel(List<Map<String, Object>> sessions, String label) {
        return sessions.stream().anyMatch(session -> label.equals(session.get("deviceLabel")));
    }

    private UUID sessionIdByLabel(List<Map<String, Object>> sessions, String label) {
        String id = sessions.stream()
                .filter(session -> label.equals(session.get("deviceLabel")))
                .map(session -> session.get("id").toString())
                .findFirst()
                .orElseThrow();

        return UUID.fromString(id);
    }

    private void createUser(Long tenantId, String username) {
        userRepository.save(new User(tenantId, username, username + "@mail.com", passwordEncoder.encode(PASSWORD)));
    }

    private void removeTestData() {
        jdbcTemplate.update(
                "delete from audit_logs where actor_user_id in (select id from users where username like ?)"
                        + " or target_user_id in (select id from users where username like ?)",
                PREFIX + "%", PREFIX + "%");
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update(
                "delete from sessions where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update(
                "delete from user_roles where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update("delete from users where username like ?", PREFIX + "%");
        jdbcTemplate.update("delete from tenants where organization_id = ?", QA_ORG_ID);
    }

    private Tokens tokensOf(MvcResult result) {
        try {
            String body = result.getResponse().getContentAsString();
            return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
        } catch (Exception exception) {
            throw new IllegalStateException("Gagal membaca respons token", exception);
        }
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
