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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Membuktikan binding antara klaim tenant_id pada access token dan tenant
 * yang diresolusi dari header X-Organization-ID (ADR-0019).
 *
 * Kenapa tajam: isolasi tenant sebelumnya hanya partisi data. Header
 * dikendalikan pemanggil, sehingga token org-a yang valid cukup dikirim
 * bersama header org-b untuk beroperasi di tenant b. Uji ini mengeksekusi
 * alur nyata (login org-a, lalu panggil endpoint org-b) dan memastikan
 * jawabannya 401 â€” bukan 200.
 *
 * Jalur yang dikecualikan (ADR-0018 poin 6): penukaran refresh token
 * mengabaikan header dan menambatkan diri ke tenant tersimpan pada token;
 * itu diuji di sini juga supaya pengecualian itu eksplisit, bukan tersirat.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenantBindingIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final String DEFAULT_ORG_ID = "org-default";
    private static final String QA_ORG_ID = "org-qa-binding";
    private static final String PREFIX = "bindingqa-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        removeTestData();
        jdbcTemplate.queryForObject(
                "insert into tenants (active, created_at, name, organization_id)"
                        + " values (true, now(), 'QA Binding Tenant', ?)"
                        + " on conflict (organization_id) do update set name = excluded.name"
                        + " returning id",
                Long.class, QA_ORG_ID);
        createAdminUser(PREFIX + "admin");
    }

    @AfterEach
    void tearDown() {
        removeTestData();
    }

    @Test
    void adminTokenUsedWithOtherTenantHeader_returns401() throws Exception {
        String token = login(PREFIX + "admin", DEFAULT_ORG_ID);

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token does not match the resolved organization"));
    }

    @Test
    void mismatchedTenantRequest_isCountedAsTenantMismatchRejection() throws Exception {
        String token = login(PREFIX + "admin", DEFAULT_ORG_ID);
        long before = rejectionStatCount("tenant_mismatch");

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isUnauthorized());

        long after = rejectionStatCount("tenant_mismatch");
        assertTrue(after > before, "penolakan tenant_mismatch harus tercatat di agregat per-menit");
    }

    @Test
    void adminTokenUsedWithOwnTenantHeader_isAllowed() throws Exception {
        String token = login(PREFIX + "admin", DEFAULT_ORG_ID);

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-ID", DEFAULT_ORG_ID))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedEndpoint_withOtherTenantHeader_returns401_notData() throws Exception {
        String token = login(PREFIX + "admin", DEFAULT_ORG_ID);

        mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token does not match the resolved organization"));
    }

    @Test
    void refreshFlow_withOtherTenantHeader_succeedsAndIssuesBoundAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", DEFAULT_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + PREFIX + "admin\",\"password\":\"" + PASSWORD
                                + "\",\"deviceLabel\":\"laptop\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(),
                "$.refreshToken");

        // ADR-0018 poin 6: penukaran refresh mengabaikan header untuk tenant;
        // token baru tetap milik org-default.
        MvcResult refreshResult = mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", QA_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String newAccessToken = JsonPath.read(refreshResult.getResponse().getContentAsString(),
                "$.accessToken");

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + newAccessToken)
                        .header("X-Organization-ID", QA_ORG_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + newAccessToken)
                        .header("X-Organization-ID", DEFAULT_ORG_ID))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenPresentedAsBearer_isRejected() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", DEFAULT_ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + PREFIX + "admin\",\"password\":\"" + PASSWORD
                                + "\",\"deviceLabel\":\"laptop\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(),
                "$.refreshToken");

        mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + refreshToken)
                        .header("X-Organization-ID", DEFAULT_ORG_ID))
                .andExpect(status().isUnauthorized());
    }

    private void createAdminUser(String username) {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        User admin = new User(DEFAULT_TENANT_ID, username, username + "@mail.com",
                passwordEncoder.encode(PASSWORD));
        admin.replaceRoles(Set.of(adminRole));
        userRepository.save(admin);
    }

    private String login(String username, String organizationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD
                                + "\",\"deviceLabel\":\"laptop\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private long rejectionStatCount(String reason) {
        // Agregat 401 tenant_mismatch dicatat untuk tenant hasil resolusi
        // (atau tenant default bila tidak ada); cukup pastikan jumlah baris
        // dengan alasan ini bertambah.
        Long count = jdbcTemplate.queryForObject(
                "select coalesce(sum(rejection_count), 0) from security_rejection_stats where reason = ?",
                Long.class, reason);

        return count == null ? 0 : count;
    }

    private void removeTestData() {
        jdbcTemplate.update(
                "delete from security_rejection_stats where tenant_id"
                        + " in (select id from tenants where organization_id in (?, 'org-default'))",
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
}
