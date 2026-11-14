package id.osprey.iamservice.adapter.rest;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Menguji kontrak daftar jejak audit lewat HTTP.
 *
 * Kenapa perlu tes terpisah dari AuditServiceTest: unit test hanya memeriksa
 * parameter yang dikirim ke repository. Di sini yang dibuktikan adalah
 * perilaku yang dilihat pemanggil API, yaitu urutan hasil yang stabil dan
 * penolakan properti sort yang tidak dikenal dengan status 400.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditControllerIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final Long TENANT_ID = 1L;
    private static final String PREFIX = "audittest-";

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

    private Long adminId;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        User admin = new User(TENANT_ID, PREFIX + "admin", PREFIX + "admin@mail.com",
                passwordEncoder.encode(PASSWORD));
        admin.replaceRoles(Set.of(adminRole));
        adminId = userRepository.save(admin).getId();

        adminToken = login(PREFIX + "admin");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "delete from audit_logs where action like ? or actor_user_id = ? or target_user_id = ?",
                PREFIX + "%", adminId, adminId);
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update(
                "delete from user_roles where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update("delete from users where username like ?", PREFIX + "%");
    }

    @Test
    void listAuditLogs_withoutSort_returnsNewestFirstWithIdAsTiebreaker() throws Exception {
        Instant newest = Instant.now().plusSeconds(7200);

        Long olderId = insertLog(PREFIX + "older", newest.minusSeconds(60));
        Long firstNewestId = insertLog(PREFIX + "newest-a", newest);
        Long secondNewestId = insertLog(PREFIX + "newest-b", newest);

        mockMvc.perform(get("/v1/audit-logs")
                        .param("size", "3")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(secondNewestId))
                .andExpect(jsonPath("$.content[1].id").value(firstNewestId))
                .andExpect(jsonPath("$.content[2].id").value(olderId));
    }

    @Test
    void listAuditLogs_withExplicitSort_respectsRequestedDirection() throws Exception {
        Instant base = Instant.now().plusSeconds(7200);

        insertLog("0-" + PREFIX + "a", base);
        insertLog("0-" + PREFIX + "b", base);

        mockMvc.perform(get("/v1/audit-logs")
                        .param("size", "2")
                        .param("sort", "action,asc")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("0-" + PREFIX + "a"))
                .andExpect(jsonPath("$.content[1].action").value("0-" + PREFIX + "b"));
    }

    @Test
    void listAuditLogs_withUnknownSortProperty_isRejectedWithBadRequest() throws Exception {
        mockMvc.perform(get("/v1/audit-logs")
                        .param("sort", "details,desc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not allowed")));
    }

    private Long insertLog(String action, Instant createdAt) {
        return jdbcTemplate.queryForObject(
                "insert into audit_logs (actor_user_id, action, created_at, tenant_id) values (?, ?, ?, ?) returning id",
                Long.class, adminId, action, Timestamp.from(createdAt), TENANT_ID);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
