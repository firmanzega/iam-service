package id.osprey.iamservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    private static final Instant MULAI = Instant.parse("2026-01-01T10:00:00Z");

    private RateLimitFilter filter;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(MULAI, ZoneOffset.UTC);
        filter = new RateLimitFilter(properties(), new JsonMapper(), clock);
    }

    private IamProperties properties() {
        return new IamProperties(
                new IamProperties.Jwt("kunci-rahasia-yang-panjang-sekali-1234567890",
                        "iam-service", Duration.ofMinutes(15), Duration.ofDays(7)),
                new IamProperties.Cors(List.of("http://localhost:3000")),
                new IamProperties.Login(5, Duration.ofMinutes(15)),
                new IamProperties.RateLimit(3, Duration.ofMinutes(1), Duration.ofMinutes(10)),
                new IamProperties.RefreshTokenCleanup(true, Duration.ofHours(1)));
    }

    private MockHttpServletResponse callFilter(String uri) throws Exception {
        return callFilter(filter, uri, "10.0.0.1");
    }

    private MockHttpServletResponse callFilter(RateLimitFilter target, String uri, String remoteAddr)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();

        target.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void requestsWithinLimit_arePassedThrough() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = callFilter("/auth/login");
            assertEquals(200, response.getStatus(), "permintaan ke-" + (i + 1) + " seharusnya lolos");
        }
    }

    @Test
    void requestBeyondLimit_isRejectedWithTooManyRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            callFilter("/auth/login");
        }

        MockHttpServletResponse response = callFilter("/auth/login");

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Too many requests"));
    }

    @Test
    void differentClientHasItsOwnCounter() throws Exception {
        for (int i = 0; i < 3; i++) {
            callFilter("/auth/login");
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void requestsOutsideAuthPath_areNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = callFilter("/users");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void counterIsClearedAfterTheWindowPassed() throws Exception {
        MutableClock moving = new MutableClock(MULAI);
        RateLimitFilter local = new RateLimitFilter(properties(), new JsonMapper(), moving);

        for (int i = 0; i < 3; i++) {
            callFilter(local, "/auth/login", "10.0.0.1");
        }

        // Majukan jam melewati panjang jendela pada instance yang sama,
        // supaya yang diuji benar-benar pemangkasan antrean, bukan filter baru.
        moving.advance(Duration.ofSeconds(61));

        MockHttpServletResponse response = callFilter(local, "/auth/login", "10.0.0.1");
        assertEquals(200, response.getStatus());
    }

    @Test
    void removeStaleClients_dropsOnlyClientsOutsideTheWindow() throws Exception {
        MutableClock moving = new MutableClock(MULAI);
        RateLimitFilter local = new RateLimitFilter(properties(), new JsonMapper(), moving);

        callFilter(local, "/auth/login", "10.0.0.1");

        moving.advance(Duration.ofSeconds(30));
        callFilter(local, "/auth/login", "10.0.0.2");

        // Klien pertama berumur 62 detik, klien kedua 32 detik.
        moving.advance(Duration.ofSeconds(32));
        assertEquals(1, local.removeStaleClients(), "hanya klien di luar jendela yang boleh dibuang");

        // Setelah 40 detik lagi, klien kedua juga sudah basi.
        moving.advance(Duration.ofSeconds(40));
        assertEquals(1, local.removeStaleClients());
    }

    /**
     * Jam yang bisa dimajukan tanpa membuat instance filter baru. Diperlukan
     * untuk menguji pemangkasan pada objek yang sama.
     */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
