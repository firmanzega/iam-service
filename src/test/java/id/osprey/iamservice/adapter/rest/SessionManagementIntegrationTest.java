package id.osprey.iamservice.adapter.rest;

import com.jayway.jsonpath.JsonPath;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Membuktikan sesi per perangkat bekerja lewat HTTP dan database sungguhan.
 *
 * Yang tidak bisa dibuktikan unit test:
 *      - Logout satu perangkat tidak ikut mematikan token perangkat lain.
 *      - Sesi milik pengguna lain dijawab 404, sama seperti sesi yang tidak ada.
 *      - Klaim session_id pada token benar-benar menunjuk baris sesi yang sama.
 */
@SpringBootTest(properties = "iam.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionManagementIntegrationTest {

    private static final String PASSWORD = "Rahasia123";
    private static final Long TENANT_ID = 1L;
    private static final String PREFIX = "sessiontest-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        removeTestData();
        createUser(PREFIX + "satu");
        createUser(PREFIX + "dua");
    }

    @AfterEach
    void tearDown() {
        removeTestData();
    }

    @Test
    void twoLogins_listsBothSessionsAndMarksOnlyTheCallerAsCurrent() throws Exception {
        login(PREFIX + "satu", "laptop");
        Tokens telepon = login(PREFIX + "satu", "telepon");

        List<Map<String, Object>> sessions = listSessions(telepon.accessToken());

        assertEquals(2, sessions.size());
        assertTrue(hasLabel(sessions, "laptop"));
        assertTrue(hasLabel(sessions, "telepon"));

        Map<String, Object> current = sessions.stream()
                .filter(session -> Boolean.TRUE.equals(session.get("current")))
                .findFirst()
                .orElseThrow();

        assertEquals("telepon", current.get("deviceLabel"),
                "sesi pada token pemanggil harus ditandai current");
        assertEquals(1, sessions.stream()
                .filter(session -> Boolean.TRUE.equals(session.get("current")))
                .count());
    }

    @Test
    void logoutFromOneDevice_keepsTheOtherDeviceAlive() throws Exception {
        Tokens laptop = login(PREFIX + "satu", "laptop");
        Tokens telepon = login(PREFIX + "satu", "telepon");

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + laptop.accessToken())
                        .header("X-Organization-ID", "org-default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + laptop.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());

        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());

        Tokens teleponBaru = tokensOf(
                refresh(telepon.refreshToken()).andExpect(status().isOk()).andReturn());

        List<Map<String, Object>> sessions = listSessions(teleponBaru.accessToken());
        assertEquals(1, sessions.size(), "sesi perangkat lain tidak boleh ikut dicabut");
        assertEquals("telepon", sessions.get(0).get("deviceLabel"));
    }

    @Test
    void revokeOwnOtherDevice_revokesOnlyThatDevice() throws Exception {
        Tokens laptop = login(PREFIX + "satu", "laptop");
        Tokens telepon = login(PREFIX + "satu", "telepon");

        UUID laptopSessionId = sessionIdByLabel(listSessions(telepon.accessToken()), "laptop");

        mockMvc.perform(delete("/v1/auth/sessions/{id}", laptopSessionId)
                        .header("Authorization", "Bearer " + telepon.accessToken())
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isNoContent());

        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());

        Tokens teleponBaru = tokensOf(
                refresh(telepon.refreshToken()).andExpect(status().isOk()).andReturn());

        assertEquals(1, listSessions(teleponBaru.accessToken()).size());
    }

    @Test
    void revokeSessionOfAnotherUser_isNotFound() throws Exception {
        Tokens milikSatu = login(PREFIX + "satu", "laptop");
        Tokens milikDua = login(PREFIX + "dua", "telepon");

        UUID sessionMilikDua = sessionIdByLabel(listSessions(milikDua.accessToken()), "telepon");

        mockMvc.perform(delete("/v1/auth/sessions/{id}", sessionMilikDua)
                        .header("Authorization", "Bearer " + milikSatu.accessToken())
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/v1/auth/sessions/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + milikSatu.accessToken())
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isNotFound());

        refresh(milikDua.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void listSessions_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/auth/sessions")
                        .header("X-Organization-ID", "org-default"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenCarriesSessionIdThatMatchesStoredSession() throws Exception {
        Tokens tokens = login(PREFIX + "satu", "laptop");

        String sessionId = com.nimbusds.jwt.SignedJWT.parse(tokens.accessToken())
                .getJWTClaimsSet().getStringClaim("session_id");

        assertNotNull(sessionId);
        UUID.fromString(sessionId);

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from sessions where id = ?::uuid"
                        + " and user_id = (select id from users where username = ?)",
                Integer.class, sessionId, PREFIX + "satu");

        assertEquals(1, rows, "klaim session_id harus menunjuk sesi yang benar-benar tersimpan");
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

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private Tokens tokensOf(MvcResult result) {
        try {
            String body = result.getResponse().getContentAsString();
            return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
        } catch (Exception exception) {
            throw new IllegalStateException("Gagal membaca respons token", exception);
        }
    }

    private List<Map<String, Object>> listSessions(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Organization-ID", "org-default"))
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

    private void createUser(String username) {
        userRepository.save(new User(TENANT_ID, username, username + "@mail.com", passwordEncoder.encode(PASSWORD)));
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
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
