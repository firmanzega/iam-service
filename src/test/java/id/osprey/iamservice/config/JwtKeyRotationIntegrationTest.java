package id.osprey.iamservice.config;

import com.nimbusds.jwt.SignedJWT;
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
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Membuktikan rotasi kunci bekerja lewat jalur HTTP yang sesungguhnya.
 *
 * Kenapa ada tes ini meskipun key ring sudah diuji sendiri:
 *      - Tes unit membuktikan pemilihan kunci benar. Tes ini membuktikan
 *        decoder itu benar-benar dipasang pada security filter chain, sehingga
 *        token dengan kunci lama tidak ditolak sebelum sempat dipakai.
 *      - Yang diperiksa adalah status 403, bukan 401. Status 403 berarti
 *        identitas diterima tetapi role-nya kurang; itu bukti autentikasi
 *        berhasil. Status 401 berarti token ditolak.
 */
@SpringBootTest(properties = {
        "iam.jwt.secret=kunci-aktif-integrasi-yang-cukup-panjang-1234567890",
        "iam.jwt.previous-secret=kunci-lama-integrasi-yang-juga-cukup-panjang-0987654321",
        "iam.rate-limit.max-requests=10000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtKeyRotationIntegrationTest {

    private static final String CURRENT_SECRET = "kunci-aktif-integrasi-yang-cukup-panjang-1234567890";
    private static final String PREVIOUS_SECRET = "kunci-lama-integrasi-yang-juga-cukup-panjang-0987654321";
    private static final String UNKNOWN_SECRET = "kunci-asing-integrasi-yang-juga-cukup-panjang-13579";
    private static final String PASSWORD = "Rahasia123";
    private static final String PREFIX = "rotasitest-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtKeyRing keyRing;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SecretKey currentKey = JwtTestTokens.key(CURRENT_SECRET);
    private final SecretKey previousKey = JwtTestTokens.key(PREVIOUS_SECRET);
    private final SecretKey unknownKey = JwtTestTokens.key(UNKNOWN_SECRET);

    @BeforeEach
    void setUp() {
        createUser(PREFIX + "pengguna");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update(
                "delete from user_roles where user_id in (select id from users where username like ?)",
                PREFIX + "%");
        jdbcTemplate.update("delete from users where username like ?", PREFIX + "%");
    }

    @Test
    void tokenSignedWithCurrentKey_isAccepted() throws Exception {
        String token = JwtTestTokens.accessToken(currentKey, keyRing.currentKeyId());

        callProtectedEndpoint(token).andExpect(status().isForbidden());
    }

    @Test
    void tokenSignedWithPreviousKey_isAcceptedDuringRotation() throws Exception {
        String token = JwtTestTokens.accessToken(previousKey, JwtKeyRing.keyId(previousKey));

        callProtectedEndpoint(token).andExpect(status().isForbidden());
    }

    @Test
    void legacyTokenWithoutKidSignedWithPreviousKey_isAcceptedDuringRotation() throws Exception {
        String token = JwtTestTokens.accessToken(previousKey, null);

        callProtectedEndpoint(token).andExpect(status().isForbidden());
    }

    @Test
    void tokenWithUnknownKid_isRejected() throws Exception {
        String token = JwtTestTokens.accessToken(currentKey, "kid-tidak-dikenal");

        callProtectedEndpoint(token).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithUnknownKeyClaimingCurrentKid_isRejected() throws Exception {
        String token = JwtTestTokens.accessToken(unknownKey, keyRing.currentKeyId());

        callProtectedEndpoint(token).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        String token = JwtTestTokens.accessToken(
                currentKey, keyRing.currentKeyId(), JwtTestTokens.ISSUER, Instant.now().minusSeconds(60));

        callProtectedEndpoint(token).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromForeignIssuer_isRejected() throws Exception {
        String token = JwtTestTokens.accessToken(
                currentKey, keyRing.currentKeyId(), "sistem-lain", Instant.now().plusSeconds(300));

        callProtectedEndpoint(token).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIssuesTokenWithCurrentKeyId() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + PREFIX + "pengguna\",\"password\":\""
                                + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf('"', start);
        String accessToken = body.substring(start, end);

        SignedJWT parsed = SignedJWT.parse(accessToken);
        assertEquals(keyRing.currentKeyId(), parsed.getHeader().getKeyID());
        assertEquals(PREFIX + "pengguna", parsed.getJWTClaimsSet().getStringClaim("username"));
    }

    private ResultActions callProtectedEndpoint(String token) throws Exception {
        return mockMvc.perform(get("/v1/users").header("Authorization", "Bearer " + token));
    }

    private void createUser(String username) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        User user = new User(username, username + "@mail.com", passwordEncoder.encode(PASSWORD));
        user.replaceRoles(Set.of(role));
        userRepository.save(user);
    }
}
