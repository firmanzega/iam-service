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
        filter = new RateLimitFilter(
                new IamProperties(
                        new IamProperties.Jwt("kunci-rahasia-yang-panjang-sekali-1234567890",
                                "iam-service", Duration.ofMinutes(15), Duration.ofDays(7)),
                        new IamProperties.Cors(List.of("http://localhost:3000")),
                        new IamProperties.Login(5, Duration.ofMinutes(15)),
                        new IamProperties.RateLimit(3, Duration.ofMinutes(1))),
                new JsonMapper(),
                clock);
    }

    private MockHttpServletResponse callFilter(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
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
        for (int i = 0; i < 3; i++) {
            callFilter("/auth/login");
        }

        // Majukan jam melewati panjang jendela.
        clock = Clock.fixed(MULAI.plusSeconds(61), ZoneOffset.UTC);
        filter = new RateLimitFilter(
                new IamProperties(
                        new IamProperties.Jwt("kunci-rahasia-yang-panjang-sekali-1234567890",
                                "iam-service", Duration.ofMinutes(15), Duration.ofDays(7)),
                        new IamProperties.Cors(List.of("http://localhost:3000")),
                        new IamProperties.Login(5, Duration.ofMinutes(15)),
                        new IamProperties.RateLimit(3, Duration.ofMinutes(1))),
                new JsonMapper(),
                clock);

        MockHttpServletResponse response = callFilter("/auth/login");
        assertEquals(200, response.getStatus());
    }
}
