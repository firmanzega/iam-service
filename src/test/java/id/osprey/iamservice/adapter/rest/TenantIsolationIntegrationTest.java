package id.osprey.iamservice.adapter.rest;

import com.jayway.jsonpath.JsonPath;
import id.osprey.iamservice.TestcontainersConfiguration;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Prasyarat testcontainers: database dan skema V1-V11 dibangun ulang setiap
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

    @Autowired
    private RoleRepository roleRepository;

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
    void sessions_withoutOrganizationHeader_returns401() throws Exception {
        Tokens tokens = login(PREFIX + "a", "laptop", DEFAULT_ORG_ID);

        mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.containsString("X-Organization-ID")));
    }

    @Test
    void logout_withoutOrganizationHeader_returns401() throws Exception {
        Tokens tokens = login(PREFIX + "a", "laptop", DEFAULT_ORG_ID);

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessions_listsOnlyCallerTenantOwnSessions() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop", DEFAULT_ORG_ID);
        Tokens tokensB = login(PREFIX + "b", "telepon", QA_ORG_ID);

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
    void unauthorized_aggregatesPerTenant_sameMinuteSameMethodAndReason() throws Exception {
        // SEC-STATS-001: sebelum V11, unique global (window_start, http_method,
        // reason) membuat INSERT tenant kedua pada menit & alasan yang sama
        // melanggar constraint; exception ditelan recorder dan agregat tenant
        // non-default hilang diam-diam.
        long beforeDefault = rejectionStatCount(DEFAULT_TENANT_ID);
        long beforeQa = rejectionStatCount(qaTenantId);

        mockMvc.perform(get("/v1/users")
                        .header("X-Organization-ID", DEFAULT_ORG_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/users")
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/users")
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isUnauthorized());

        assertEquals(beforeDefault + 1, rejectionStatCount(DEFAULT_TENANT_ID),
                "agregat 401 org-default harus naik satu");
        assertEquals(beforeQa + 2, rejectionStatCount(qaTenantId),
                "agregat 401 tenant QA harus naik dua, bukan tertelan constraint global");
    }

    @Test
    void users_withoutOrganizationHeader_returns401() throws Exception {
        String adminToken = loginAdmin(DEFAULT_ORG_ID, PREFIX + "admin");

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void users_withUnknownOrganization_returns401() throws Exception {
        String adminToken = loginAdmin(DEFAULT_ORG_ID, PREFIX + "admin");

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-ID", "org-does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findUserById_fromOtherTenant_isNotFound_notLeaked() throws Exception {
        String adminToken = loginAdmin(DEFAULT_ORG_ID, PREFIX + "admin");
        Long qaUserId = userIdByUsername(PREFIX + "b");

        mockMvc.perform(get("/v1/users/{id}", qaUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-ID", DEFAULT_ORG_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetPassword_fromOtherTenant_isNotFound_targetKeepsOldPassword() throws Exception {
        String adminToken = loginAdmin(DEFAULT_ORG_ID, PREFIX + "admin");
        Long qaUserId = userIdByUsername(PREFIX + "b");
        login(PREFIX + "b", "telepon", QA_ORG_ID); // bukti awal: akun QA bisa login

        mockMvc.perform(post("/v1/users/{id}/reset-password", qaUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-ID", DEFAULT_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"MemaksaMasuk1\"}"))
                .andExpect(status().isNotFound());

        // Akun QA tetap bisa login dengan password lama: reset lintas-tenant
        // tidak boleh mengubah kredensial.
        login(PREFIX + "b", "telepon", QA_ORG_ID);
    }

    @Test
    void listUsers_scopedToCallerTenant_onlyShowsOwnTenant() throws Exception {
        String defaultAdminToken = loginAdmin(DEFAULT_ORG_ID, PREFIX + "admin");
        String qaAdminToken = loginAdmin(QA_ORG_ID, PREFIX + "qa-admin");

        List<String> draft = readUsernames(defaultAdminToken, DEFAULT_ORG_ID);
        assertTrue(draft.contains(PREFIX + "a"), "admin tenant default harus melihat pengguna tenant-nya");
        assertFalse(draft.contains(PREFIX + "b"), "admin tenant default TIDAK boleh melihat pengguna tenant QA");
        assertFalse(draft.contains(PREFIX + "qa-admin"), "admin tenant default TIDAK boleh melihat admin tenant QA");

        List<String> qaView = readUsernames(qaAdminToken, QA_ORG_ID);
        assertTrue(qaView.contains(PREFIX + "b"), "admin tenant QA harus melihat pengguna tenant-nya");
        assertTrue(qaView.contains(PREFIX + "qa-admin"));
        assertFalse(qaView.contains(PREFIX + "a"), "admin tenant QA TIDAK boleh melihat pengguna tenant default");
    }

    @Test
    void revokeSession_fromOtherTenant_isNotFound_andOriginalStaysAlive() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop", DEFAULT_ORG_ID);
        Tokens tokensB = login(PREFIX + "b", "telepon", QA_ORG_ID);

        List<Map<String, Object>> sessionsA = listSessions(tokensA.accessToken(), DEFAULT_ORG_ID);
        UUID sessionA = sessionIdByLabel(sessionsA, "laptop");

        mockMvc.perform(delete("/v1/auth/sessions/{id}", sessionA)
                        .header("Authorization", "Bearer " + tokensB.accessToken())
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isNotFound());

        MvcResult refresh = mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", DEFAULT_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokensA.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("Bearer", JsonPath.read(refresh.getResponse().getContentAsString(), "$.tokenType"));
    }

    @Test
    void sessions_withUnknownOrganization_isRejectedWith401_notFallbackToDefaultTenant() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop", DEFAULT_ORG_ID);

        mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + tokensA.accessToken())
                        .header("X-Organization-ID", "org-does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withoutOrganizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + PREFIX + "a\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withUnknownOrganization_returns401() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + PREFIX + "a\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withoutOrganizationHeader_returns401() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop", DEFAULT_ORG_ID);

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokensA.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withUnknownOrganization_returns401() throws Exception {
        Tokens tokensA = login(PREFIX + "a", "laptop", DEFAULT_ORG_ID);

        mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", "org-does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokensA.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_sameIdentityInTwoTenants_bothSucceed() throws Exception {
        // V10 (ADR-0018): identitas unik per tenant, bukan global.
        createUser(DEFAULT_TENANT_ID, PREFIX + "dup");
        createUser(qaTenantId, PREFIX + "dup");

        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", DEFAULT_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + PREFIX + "dup\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", QA_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + PREFIX + "dup\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_usesStoredTokenTenant_notHeaderTenant() throws Exception {
        // ADR-0018 poin 6: sumber kebenaran tenant untuk penukaran refresh
        // token adalah token tersimpan. Header org-default dikirim untuk
        // user yang berasal dari tenant QA; sesi harus ditandai tetap satu
        // (sesi lama disentuh), bukan dibuat sesi baru di tenant salah.
        Tokens tokensB = login(PREFIX + "b", "telepon", QA_ORG_ID);

        mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", DEFAULT_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokensB.refreshToken() + "\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> sessions = listSessions(tokensB.accessToken(), QA_ORG_ID);
        assertEquals(1, sessions.size(),
                "penukaran refresh harus melanjutkan sesi tenant tersimpan, bukan membuat sesi baru");
    }

    private String loginAdmin(String organizationId, String username) throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        User admin = new User(tenantIdOf(organizationId), username, username + "@mail.com", passwordEncoder.encode(PASSWORD));
        admin.replaceRoles(Set.of(adminRole));
        userRepository.save(admin);

        return login(username, "audit", organizationId).accessToken();
    }

    private Long tenantIdOf(String organizationId) {
        return jdbcTemplate.queryForObject(
                "select id from tenants where organization_id = ?", Long.class, organizationId);
    }

    private Long userIdByUsername(String username) {
        return jdbcTemplate.queryForObject(
                "select id from users where username = ?", Long.class, username);
    }

    private List<String> readUsernames(String accessToken, String organizationId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Organization-ID", organizationId))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> content = JsonPath.read(
                result.getResponse().getContentAsString(), "$.content");
        return content.stream().map(row -> row.get("username").toString()).toList();
    }

    private Tokens login(String username, String deviceLabel, String organizationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", organizationId)
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

    private long rejectionStatCount(Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "select coalesce(sum(rejection_count), 0) from security_rejection_stats"
                        + " where tenant_id = ? and http_method = 'GET' and reason = 'missing_credentials'",
                Long.class, tenantId);

        return count == null ? 0 : count;
    }

    private void removeTestData() {
        jdbcTemplate.update(
                "delete from security_rejection_stats where tenant_id"
                        + " = (select id from tenants where organization_id = ?)",
                QA_ORG_ID);
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
