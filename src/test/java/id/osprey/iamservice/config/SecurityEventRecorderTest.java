package id.osprey.iamservice.config;

import id.osprey.iamservice.application.audit.AuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SecurityEventRecorderTest {

    private final AuditService auditService = mock(AuditService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SecurityEventRecorder recorder = new SecurityEventRecorder(auditService, meterRegistry);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        meterRegistry.close();
    }

    @Test
    void unauthorizedOnlyCountsMetricAndDoesNotWriteAudit() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");

        recorder.recordUnauthorized(request, "kredensial tidak sah");

        assertEquals(1, counterValue("unauthorized", "GET"));
        verify(auditService, never()).recordIndependently(any(), any(), any(), any());
    }

    @Test
    void accessDeniedWritesAuditWithActorFromAuthentication() {
        authenticateAs("42");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/roles");

        recorder.recordAccessDenied(request, "akses ditolak");

        assertEquals(1, counterValue("access_denied", "POST"));

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordIndependently(eq(42L), eq("ACCESS_DENIED"), eq(null), details.capture());
        assertTrue(details.getValue().contains("POST /roles"));
    }

    @Test
    void accessDeniedWithoutNumericSubjectUsesNullActor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null));

        recorder.recordAccessDenied(new MockHttpServletRequest("GET", "/users"), "akses ditolak");

        verify(auditService).recordIndependently(isNull(), eq("ACCESS_DENIED"), isNull(), any());
    }

    @Test
    void auditFailureDoesNotBreakTheRejectionResponse() {
        authenticateAs("7");
        doThrow(new IllegalStateException("database sedang sibuk"))
                .when(auditService).recordIndependently(any(), any(), any(), any());

        recorder.recordAccessDenied(new MockHttpServletRequest("GET", "/users"), "akses ditolak");

        assertEquals(1, counterValue("access_denied", "GET"));
    }

    private void authenticateAs(String subject) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null, List.of()));
    }

    private double counterValue(String reason, String method) {
        var counter = meterRegistry.find(SecurityEventRecorder.METRIC_NAME)
                .tags("reason", reason, "method", method)
                .counter();

        return counter == null ? 0 : counter.count();
    }
}
