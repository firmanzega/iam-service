package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.maintenance.MaintenanceState;
import id.osprey.iamservice.config.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Denies every request with 503 while an audit write-freeze is active.
 *
 * The rule is default-deny: future API versions and new endpoints are covered
 * without editing a path list. Only the healthcheck and the maintenance toggle
 * are exempt, because the toggle is the only way to unfreeze at runtime.
 */
public class AuditWriteFreezeFilter extends OncePerRequestFilter {

    private static final String RETRY_AFTER_SECONDS = "120";

    private final MaintenanceState maintenanceState;
    private final SecurityErrorWriter errorWriter;

    public AuditWriteFreezeFilter(MaintenanceState maintenanceState, SecurityErrorWriter errorWriter) {
        this.maintenanceState = maintenanceState;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (maintenanceState.isAuditFreeze() && !isExempt(request.getRequestURI())) {
            response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
            errorWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Service is in maintenance mode; retry shortly");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExempt(String uri) {
        return uri.equals("/actuator/health")
                || uri.equals("/internal/maintenance")
                || uri.startsWith("/internal/maintenance/");
    }
}
