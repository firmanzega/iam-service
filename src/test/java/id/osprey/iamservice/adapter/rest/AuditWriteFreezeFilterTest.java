package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.maintenance.MaintenanceState;
import id.osprey.iamservice.config.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditWriteFreezeFilterTest {

    private final MaintenanceState state = new MaintenanceState(false);
    private final AuditWriteFreezeFilter filter =
            new AuditWriteFreezeFilter(state, new SecurityErrorWriter(new ObjectMapper()));

    @Test
    void freezeRejectsEveryRequestWith503AndRetryAfter() throws Exception {
        state.enable("ops-1", "hash backfill");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(chainCalled.get());
        assertEquals(503, response.getStatus());
        assertEquals("120", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("maintenance mode"));
    }

    @Test
    void freezeAlsoRejectsFutureApiVersions() throws Exception {
        state.enable("ops-1", "hash backfill");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/v2/future"),
                response, (req, res) -> { });

        assertEquals(503, response.getStatus());
    }

    @Test
    void healthEndpointRemainsAvailableDuringFreeze() throws Exception {
        state.enable("ops-1", "hash backfill");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(), (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
    }

    @Test
    void maintenanceToggleRemainsAvailableDuringFreeze() throws Exception {
        state.enable("ops-1", "hash backfill");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("POST", "/internal/maintenance/disable"),
                new MockHttpServletResponse(), (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
    }

    @Test
    void withoutFreezeRequestPassesThrough() throws Exception {
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("POST", "/v1/auth/login"),
                new MockHttpServletResponse(), (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
    }
}
