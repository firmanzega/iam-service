package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.TestcontainersConfiguration;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.tenant.TenantContext;
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
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for Phase 1 tenant provisioning (ADR-0020, ADR-0023).
 *
 * The risk is not a missing row but a privilege or idempotency bug: a tenant
 * ADMIN provisioning a tenant, a replay creating duplicates, or an ADMIN
 * landing in the caller's tenant instead of the new one.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenantProvisioningIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final String USERNAME_PREFIX = "provtest-";
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

    private String organizationId;
    private String name;
    private String opsToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        organizationId = USERNAME_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        name = "Provisioned " + organizationId;

        Role opsRole = roleRepository.findByName("OPS").orElseThrow();
        User ops = new User(TENANT_ID, USERNAME_PREFIX + "ops", USERNAME_PREFIX + "ops@mail.com",
                passwordEncoder.encode(PASSWORD));
        ops.replaceRoles(Set.of(opsRole));
        userRepository.save(ops);

        User member = new User(TENANT_ID, USERNAME_PREFIX + "member", USERNAME_PREFIX + "member@mail.com",
                passwordEncoder.encode(PASSWORD));
        userRepository.save(member);

        opsToken = login(USERNAME_PREFIX + "ops", "org-default");
        memberToken = login(USERNAME_PREFIX + "member", "org-default");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from refresh_tokens where user_id in "
                + "(select id from users where tenant_id in "
                + "(select id from tenants where organization_id = ?))", organizationId);
        jdbcTemplate.update("delete from audit_logs where details like ? or actor_user_id in "
                + "(select id from users where tenant_id in "
                + "(select id from tenants where organization_id = ?))", "%" + organizationId + "%", organizationId);
        jdbcTemplate.update("delete from user_roles where user_id in "
                + "(select id from users where tenant_id in "
                + "(select id from tenants where organization_id = ?))", organizationId);
        jdbcTemplate.update("delete from users where tenant_id in "
                + "(select id from tenants where organization_id = ?)", organizationId);
        jdbcTemplate.update("delete from tenants where organization_id = ?", organizationId);

        jdbcTemplate.update("delete from refresh_tokens where user_id in "
                + "(select id from users where username like ?)", USERNAME_PREFIX + "%");
        jdbcTemplate.update("delete from audit_logs where actor_user_id in "
                + "(select id from users where username like ?) or details like ?",
                USERNAME_PREFIX + "%", "%" + organizationId + "%");
        jdbcTemplate.update("delete from user_roles where user_id in "
                + "(select id from users where username like ?)", USERNAME_PREFIX + "%");
        jdbcTemplate.update("delete from users where username like ?", USERNAME_PREFIX + "%");
        TenantContext.clear();
    }

    private String login(String username, String organization) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Organization-ID", organization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private String tenantBody() {
        return "{\"organizationId\":\"" + organizationId + "\",\"name\":\"" + name + "\"}";
    }

    private String adminBody() {
        return "{\"username\":\"" + organizationId + "-admin\","
                + "\"email\":\"" + organizationId + "@mail.com\","
                + "\"password\":\"" + PASSWORD + "\"}";
    }

    @Test
    void rejectsTenantProvisioningFromNonOpsCaller() throws Exception {
        mockMvc.perform(post("/v1/tenants")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void provisionIsIdempotentAndRejectsConflictingName() throws Exception {
        mockMvc.perform(post("/v1/tenants")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(organizationId))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/v1/tenants")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationId));

        mockMvc.perform(post("/v1/tenants")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + organizationId + "\",\"name\":\"Other name\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void bootstrapCreatesAdminInNewTenantAndIsIdempotent() throws Exception {
        mockMvc.perform(post("/v1/tenants/" + organizationId + "/bootstrap-admin")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/v1/tenants")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/tenants/" + organizationId + "/bootstrap-admin")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(organizationId + "-admin"))
                .andExpect(jsonPath("$.roles", hasItem("ADMIN")));

        mockMvc.perform(post("/v1/tenants/" + organizationId + "/bootstrap-admin")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().isOk());

        String adminToken = login(organizationId + "-admin", organizationId);
        mockMvc.perform(get("/v1/tenants/current")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-ID", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationId));
    }

    @Test
    void recordsProvisioningAuditWithActorAndOrganization() throws Exception {
        mockMvc.perform(post("/v1/tenants")
                        .header("Authorization", "Bearer " + opsToken)
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody()))
                .andExpect(status().isCreated());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'TENANT_PROVISIONED' and details like ?",
                Integer.class, "%" + organizationId + "%");
        assertTrue(count != null && count >= 1, "TENANT_PROVISIONED audit row must exist");

        Integer actorMatches = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'TENANT_PROVISIONED' "
                        + "and details like ? and actor_user_id is not null",
                Integer.class, "%" + organizationId + "%");
        assertEquals(count, actorMatches, "every provisioning audit row must carry an actor");
    }
}
