package id.osprey.iamservice.adapter.rest;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Menguji pemeriksaan keutuhan rantai jejak audit lewat HTTP.
 *
 * Kenapa memakai container database sendiri, bukan TestcontainersConfiguration
 * yang dipakai bersama:
 *      - Beberapa kelas tes lain menghapus baris audit_logs pada @AfterEach
 *        sebagai bagian dari pembersihan. Bagi rantai hash, penghapusan baris
 *        adalah tamper. Kalau container dipakai bersama, tes verifikasi bisa
 *        melihat baris yang hilang karena jadwal tes lain, bukan karena
 *        perlakuan yang memang dirancang tes ini.
 *      - Container terpisah membuat keadaan awal selalu sama: skema dibangun
 *        ulang oleh Flyway, lalu tabel audit diisi hanya oleh tes ini.
 *
 * Biayanya satu container tambahan. Itu disengaja: hasil tes yang bergantung
 * pada urutan kelas tes lain adalah sumber kegagalan yang sulit dilacak.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(AuditChainVerificationIntegrationTest.DedicatedDatabaseConfiguration.class)
class AuditChainVerificationIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final Long TENANT_ID = 1L;
    private static final String PREFIX = "chaintest-";

    /**
     * Container khusus kelas ini. Dipisahkan dari container bersama supaya
     * pembersihan tabel audit di kelas tes lain tidak terlihat sebagai tamper.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class DedicatedDatabaseConfiguration {

        private static final PostgreSQLContainer<?> POSTGRES =
                new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

        static {
            POSTGRES.start();
        }

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return POSTGRES;
        }
    }

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
    private String auditorToken;

    @BeforeEach
    void setUp() throws Exception {
        // Rantai dimulai dari keadaan kosong supaya jumlah entri yang
        // diperiksa bisa dibandingkan dengan isi tabel tanpa sisa tes lain.
        jdbcTemplate.update("delete from audit_logs");

        adminId = createUser("admin", "ADMIN");
        createUser("auditor", "AUDITOR");

        adminToken = login(PREFIX + "admin");
        auditorToken = login(PREFIX + "auditor");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from audit_logs");
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update(
                "delete from user_roles where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update("delete from users where username like ?", PREFIX + "%");
    }

    @Test
    void untamperedChain_isReportedValidAndChecksEveryEntry() throws Exception {
        insertLog("VERIFY_VALID_A");
        insertLog("VERIFY_VALID_B");
        insertLog("VERIFY_VALID_C");

        long total = countAuditLogs();

        mockMvc.perform(get("/v1/audit-logs/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.checkedEntries").value((int) total))
                .andExpect(jsonPath("$.firstBrokenEntryId").doesNotExist());
    }

    @Test
    void changedDetails_areReportedAsHashMismatch() throws Exception {
        Long changedId = insertLog("VERIFY_TAMPER");
        insertLog("VERIFY_TAMPER_LATER");

        // Trigger hanya berjalan pada insert, jadi perubahan isi baris bisa
        // dibuat langsung untuk meniru penulisan yang melewati rantai.
        jdbcTemplate.update("update audit_logs set details = ? where id = ?",
                "diubah setelah hash dihitung", changedId);

        mockMvc.perform(get("/v1/audit-logs/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstBrokenEntryId").value(changedId))
                .andExpect(jsonPath("$.reason").value("HASH_MISMATCH"));
    }

    @Test
    void deletedMiddleEntry_isReportedAsMissingPredecessor() throws Exception {
        insertLog("VERIFY_DELETE_A");
        Long removedId = insertLog("VERIFY_DELETE_B");
        Long afterRemovedId = insertLog("VERIFY_DELETE_C");

        jdbcTemplate.update("delete from audit_logs where id = ?", removedId);

        // Entri sesudah baris yang dihapus kehilangan pendahulunya. Itu yang
        // dilaporkan lebih dulu karena id-nya lebih kecil daripada akibat
        // lanjutan berupa entri yang tidak bisa dicapai dari awal rantai.
        mockMvc.perform(get("/v1/audit-logs/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstBrokenEntryId").value(afterRemovedId))
                .andExpect(jsonPath("$.reason").value("MISSING_PREDECESSOR"));
    }

    @Test
    void auditor_isNotAllowedToVerifyChain() throws Exception {
        insertLog("VERIFY_AUDITOR");

        mockMvc.perform(get("/v1/audit-logs/verify")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isNotAllowedToVerifyChain() throws Exception {
        mockMvc.perform(get("/v1/audit-logs/verify"))
                .andExpect(status().isUnauthorized());
    }

    private Long createUser(String name, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(TENANT_ID, PREFIX + name, PREFIX + name + "@mail.com", passwordEncoder.encode(PASSWORD));
        user.replaceRoles(Set.of(role));

        return userRepository.save(user).getId();
    }

    private Long insertLog(String action) {
        return jdbcTemplate.queryForObject(
                "insert into audit_logs (actor_user_id, action, details, tenant_id) values (?, ?, ?, ?) returning id",
                Long.class, adminId, action, action.toLowerCase(), TENANT_ID);
    }

    private long countAuditLogs() {
        Long count = jdbcTemplate.queryForObject("select count(*) from audit_logs", Long.class);
        return count == null ? 0 : count;
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
