package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.TestcontainersConfiguration;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
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

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import id.osprey.iamservice.domain.tenant.TenantContext;

/**
 * Menguji aturan akses endpoint lewat HTTP.
 *
 * Bedanya dengan AuthServiceTest: di sini yang diuji bukan logika service,
 * melainkan apakah permintaan benar-benar ditahan atau diteruskan oleh
 * lapisan keamanan. Aturan ini adalah kontrak dengan pemanggil API, jadi
 * harus diuji dari sisi HTTP.
 *
 * Batas rate limit dinaikkan khusus untuk test ini, karena satu kelas test
 * melakukan banyak login dalam waktu singkat. Perilaku rate limiter sendiri
 * diuji terpisah di RateLimitFilterTest dengan jam yang dikendalikan.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthSecurityIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final String USERNAME_PREFIX = "sectest-";
    private static final String TEST_ROLE_PREFIX = "SECTEST";
    private static final Long TENANT_ID = 1L;

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

    @Autowired
    private MeterRegistry meterRegistry;

    private Long adminId;
    private Long memberId;
    private Long auditorId;
    private String adminToken;
    private String memberToken;
    private String auditorToken;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role auditorRole = roleRepository.findByName("AUDITOR").orElseThrow();

        User admin = new User(TENANT_ID, USERNAME_PREFIX + "admin", USERNAME_PREFIX + "admin@mail.com",
                passwordEncoder.encode(PASSWORD));
        admin.replaceRoles(Set.of(adminRole));
        adminId = userRepository.save(admin).getId();

        User member = new User(TENANT_ID, USERNAME_PREFIX + "member", USERNAME_PREFIX + "member@mail.com",
                passwordEncoder.encode(PASSWORD));
        memberId = userRepository.save(member).getId();

        User auditor = new User(TENANT_ID, USERNAME_PREFIX + "auditor", USERNAME_PREFIX + "auditor@mail.com",
                passwordEncoder.encode(PASSWORD));
        auditor.replaceRoles(Set.of(auditorRole));
        auditorId = userRepository.save(auditor).getId();

        adminToken = login(USERNAME_PREFIX + "admin");
        memberToken = login(USERNAME_PREFIX + "member");
        auditorToken = login(USERNAME_PREFIX + "auditor");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where username like ?)",
                USERNAME_PREFIX + "%");
        jdbcTemplate.update(
                "delete from audit_logs where actor_user_id in (select id from users where username like ?)"
                        + " or target_user_id in (select id from users where username like ?)",
                USERNAME_PREFIX + "%", USERNAME_PREFIX + "%");
        jdbcTemplate.update("delete from user_roles where user_id in (select id from users where username like ?)",
                USERNAME_PREFIX + "%");
        jdbcTemplate.update("delete from users where username like ?", USERNAME_PREFIX + "%");
        jdbcTemplate.update("delete from roles where name like ?", TEST_ROLE_PREFIX + "%");
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    @Test
    void listUsers_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/users").header("X-Organization-ID", "org-default"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void createUser_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/users").header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"x@mail.com\",\"password\":\"Password1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withToken_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listUsers_asRegularMember_isForbidden() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", "Bearer " + memberToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void accessDenied_isRecordedInAuditTrailAndMetric() throws Exception {
        double metricBefore = rejectedCount("access_denied", "GET");
        int auditBefore = countAccessDenied();

        mockMvc.perform(get("/v1/users").header("Authorization", "Bearer " + memberToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isForbidden());

        assertEquals(metricBefore + 1, rejectedCount("access_denied", "GET"), 0.0001);
        assertEquals(auditBefore + 1, countAccessDenied());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select actor_user_id, action, details from audit_logs where action = 'ACCESS_DENIED'"
                        + " order by id desc limit 1");

        assertEquals(memberId, ((Number) row.get("actor_user_id")).longValue());
        assertTrue(row.get("details").toString().contains("GET /v1/users"),
                "detail audit harus memuat method dan path yang ditolak");
    }

    @Test
    void unauthorized_incrementsMetricWithoutWritingAuditRow() throws Exception {
        double metricBefore = rejectedCount("unauthorized", "GET");
        int auditBefore = countAccessDenied();

        mockMvc.perform(get("/v1/users").header("X-Organization-ID", "org-default"))
                .andExpect(status().isUnauthorized());

        assertEquals(metricBefore + 1, rejectedCount("unauthorized", "GET"), 0.0001);
        assertEquals(auditBefore, countAccessDenied(),
                "penolakan tanpa kredensial tidak boleh menulis jejak audit");
    }

    @Test
    void unauthorized_isAggregatedPerMinuteAndMethod() throws Exception {
        long before = rejectionStatCount("GET", "missing_credentials");

        mockMvc.perform(get("/v1/users").header("X-Organization-ID", "org-default"))
                .andExpect(status().isUnauthorized());

        assertEquals(before + 1, rejectionStatCount("GET", "missing_credentials"));
    }

    @Test
    void invalidToken_isAggregatedWithItsOwnReason() throws Exception {
        long before = rejectionStatCount("GET", "invalid_token");

        mockMvc.perform(get("/v1/users").header("Authorization", "Bearer bukan-token-jwt")
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isUnauthorized());

        assertEquals(before + 1, rejectionStatCount("GET", "invalid_token"));
    }

    private long rejectionStatCount(String method, String reason) {
        Long count = jdbcTemplate.queryForObject(
                "select coalesce(sum(rejection_count), 0) from security_rejection_stats"
                        + " where http_method = ? and reason = ?",
                Long.class, method, reason);

        return count == null ? 0 : count;
    }

    private double rejectedCount(String reason, String method) {
        var counter = meterRegistry.find("iam.security.rejected")
                .tags("reason", reason, "method", method)
                .counter();

        return counter == null ? 0 : counter.count();
    }

    private int countAccessDenied() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'ACCESS_DENIED'", Integer.class);

        return count == null ? 0 : count;
    }

    @Test
    void listUsers_asAuditor_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/users").header("Authorization", "Bearer " + auditorToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void findUser_ownAccount_asRegularMember_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/users/{id}", memberId).header("Authorization", "Bearer " + memberToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId));
    }

    @Test
    void findUser_otherAccount_asRegularMember_isForbidden() throws Exception {
        mockMvc.perform(get("/v1/users/{id}", adminId).header("Authorization", "Bearer " + memberToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findUser_anyAccount_asAuditor_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/users/{id}", memberId).header("Authorization", "Bearer " + auditorToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId));
    }

    @Test
    void createUser_asAdmin_isAllowed() throws Exception {
        mockMvc.perform(post("/v1/users").header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME_PREFIX + "baru\",\"email\":\""
                                + USERNAME_PREFIX + "baru@mail.com\",\"password\":\"Password1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(USERNAME_PREFIX + "baru"));
    }

    @Test
    void createUser_asRegularMember_isForbidden() throws Exception {
        mockMvc.perform(post("/v1/users").header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME_PREFIX + "dilewati\",\"email\":\""
                                + USERNAME_PREFIX + "dilewati@mail.com\",\"password\":\"Password1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void listAuditLogs_asRegularMember_isForbidden() throws Exception {
        mockMvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAuditLogs_asAdmin_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void changePassword_forAnotherUser_isForbidden() throws Exception {
        mockMvc.perform(put("/v1/users/{id}/password", adminId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"BaruBanget1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_forOwnAccount_isAllowed() throws Exception {
        mockMvc.perform(put("/v1/users/{id}/password", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"BaruBanget1\"}"))
                .andExpect(status().isNoContent());

        // Check that audit log was created for token revocation
        // Note: detail may vary if tokens were already revoked from previous test runs
        Integer revokedRecords = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = ? and target_user_id = ?",
                Integer.class, "TOKENS_REVOKED", memberId);

        assertEquals(1, revokedRecords);
    }

    @Test
    void changePassword_withWrongCurrentPassword_isUnauthorized() throws Exception {
        mockMvc.perform(put("/v1/users/{id}/password", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"SalahSekali1\",\"newPassword\":\"BaruBanget1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withWrongPassword_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + USERNAME_PREFIX + "member\",\"password\":\"SalahSekali1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void login_withInvalidBody_isBadRequest() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Failed"));
    }

    @Test
    void listRoles_asMember_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/roles").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createRole_asAdmin_isCreated() throws Exception {
        mockMvc.perform(post("/v1/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + TEST_ROLE_PREFIX + "SATU\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(TEST_ROLE_PREFIX + "SATU"));
    }

    @Test
    void createRole_asMember_isForbidden() throws Exception {
        mockMvc.perform(post("/v1/roles")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + TEST_ROLE_PREFIX + "DUA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRole_unknownRole_isNotFound() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/v1/roles/{id}", 999999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRole_stillInUse_isConflict() throws Exception {
        String roleName = TEST_ROLE_PREFIX + "TIGA";

        MvcResult created = mockMvc.perform(post("/v1/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + roleName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        long roleId = ((Number) com.jayway.jsonpath.JsonPath
                .read(created.getResponse().getContentAsString(), "$.id")).longValue();

        MvcResult user = mockMvc.perform(get("/v1/users/{id}", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        long version = ((Number) com.jayway.jsonpath.JsonPath
                .read(user.getResponse().getContentAsString(), "$.version")).longValue();

        mockMvc.perform(put("/v1/users/{id}/roles", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleNames\":[\"" + roleName + "\"],\"version\":" + version + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/v1/roles/{id}", roleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivateUser_thenLogin_isForbidden() throws Exception {
        mockMvc.perform(post("/v1/users/{id}/deactivate", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + USERNAME_PREFIX + "member\",\"password\":\""
                                + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateUser_afterDeactivate_allowsLoginAgain() throws Exception {
        mockMvc.perform(post("/v1/users/{id}/deactivate", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/users/{id}/activate", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + USERNAME_PREFIX + "member\",\"password\":\""
                                + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateUser_asAdmin_isUpdated() throws Exception {
        MvcResult user = mockMvc.perform(get("/v1/users/{id}", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        long version = ((Number) com.jayway.jsonpath.JsonPath
                .read(user.getResponse().getContentAsString(), "$.version")).longValue();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/v1/users/{id}", memberId).header("X-Organization-ID", "org-default")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + USERNAME_PREFIX + "baru@mail.com\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(USERNAME_PREFIX + "baru@mail.com"));
    }

    @Test
    void logout_revokesTheRefreshToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + USERNAME_PREFIX + "member\",\"password\":\""
                                + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = com.jayway.jsonpath.JsonPath
                .read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        String refreshToken = com.jayway.jsonpath.JsonPath
                .read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/refresh")
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
