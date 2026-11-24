package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.tenant.TenantResolver;
import id.osprey.iamservice.config.SecurityEventRecorder;
import id.osprey.iamservice.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the tenant header filter seeds MDC for structured logging and
 * always clears it, including on the early-return rejection path.
 */
class TenantHeaderFilterMdcTest {

    private final TenantResolver resolver = mock(TenantResolver.class);
    private final SecurityEventRecorder recorder = mock(SecurityEventRecorder.class);
    private final TenantHeaderFilter filter =
            new TenantHeaderFilter(resolver, recorder, new ObjectMapper());

    @AfterEach
    void clearContext() {
        MDC.remove("requestId");
        MDC.remove("tenantId");
        TenantContext.clear();
    }

    @Test
    void resolvedTenant_exposesRequestIdAndTenantIdDuringChain_thenClearsThem() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/auth/sessions");
        request.addHeader("X-Organization-ID", "org-1");
        when(resolver.resolve("org-1")).thenAnswer(invocation -> {
            TenantContext.setTenantId(11L);
            return TenantResolver.Resolution.RESOLVED;
        });

        AtomicReference<String> requestIdInChain = new AtomicReference<>();
        AtomicReference<String> tenantIdInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            requestIdInChain.set(MDC.get("requestId"));
            tenantIdInChain.set(MDC.get("tenantId"));
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(requestIdInChain.get(), "requestId must be present inside the chain");
        assertEquals("11", tenantIdInChain.get());
        assertNull(MDC.get("requestId"), "requestId must not leak after the request");
        assertNull(MDC.get("tenantId"), "tenantId must not leak after the request");
    }

    @Test
    void rejectedRequest_clearsRequestIdEvenThoughChainIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users");
        when(resolver.resolve(null)).thenReturn(TenantResolver.Resolution.MISSING);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertFalse(chainCalled.get(), "missing header must short-circuit the chain");
        verify(recorder).recordUnauthorized(any(HttpServletRequest.class), eq("missing_organization"));
        assertNull(MDC.get("requestId"), "requestId must be cleared on the rejection path");
    }

}
