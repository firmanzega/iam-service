package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.config.SecurityErrorWriter;
import id.osprey.iamservice.config.SecurityEventRecorder;
import id.osprey.iamservice.config.TokenClaims;
import id.osprey.iamservice.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Filter yang memverifikasi bahwa tenant yang diresolusi dari header
 * X-Organization-ID sama dengan klaim tenant_id pada access token.
 *
 * Tanpa pemeriksaan ini, header hanya menentukan partisi data, bukan
 * otorisasi (ADR-0019): pemanggil yang memegang token valid untuk org-a
 * cukup mengganti header menjadi org-b untuk beroperasi di sana dengan
 * peran yang dibawanya. Klaim tenant_id diterbitkan sejak v1.0.x, jadi
 * pemeriksaan ini tidak membutuhkan migrasi data.
 *
 * Dikecualikan: /v1/auth/login dan /v1/auth/refresh. Keduanya adalah
 * endpoint penukaran kredensial; refresh sengaja menambatkan diri ke tenant
 * yang tersimpan pada refresh token (ADR-0018 poin 6), bukan ke header.
 *
 * Filter ini berjalan setelah BearerTokenAuthenticationFilter sehingga token
 * sudah divalidasi dan SecurityContext sudah terisi saat pemeriksaan
 * dilakukan.
 */
public class TenantBindingFilter extends OncePerRequestFilter {

    private static final String[] EXEMPT_PATHS = {
            "/v1/auth/login",
            "/v1/auth/refresh"
    };

    private static final String REJECTION_REASON = "tenant_mismatch";
    private static final String MISMATCH_MESSAGE = "Token does not match the resolved organization";

    private final SecurityEventRecorder securityEventRecorder;
    private final SecurityErrorWriter errorWriter;

    public TenantBindingFilter(SecurityEventRecorder securityEventRecorder, ObjectMapper objectMapper) {
        this.securityEventRecorder = securityEventRecorder;
        this.errorWriter = new SecurityErrorWriter(objectMapper);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = normalizePath(request);

        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long resolvedTenantId = TenantContext.getTenantId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Tidak ada identitas atau tenant yang bisa dibandingkan: jalur ini
        // diserahkan ke aturan otorisasi yang sudah ada (permitAll/401).
        if (resolvedTenantId == null || authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!claimMatchesTenant(jwt, resolvedTenantId)) {
            SecurityContextHolder.clearContext();
            securityEventRecorder.recordUnauthorized(request, REJECTION_REASON);
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, MISMATCH_MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean claimMatchesTenant(Jwt jwt, Long resolvedTenantId) {
        String claim = jwt.getClaimAsString(TokenClaims.TENANT_ID);
        if (claim == null || claim.isBlank()) {
            // Token tanpa klaim tenant tidak bisa diikat ke tenant mana pun.
            // Ditolak dengan sengaja (fail-closed).
            return false;
        }
        try {
            return Long.parseLong(claim) == resolvedTenantId;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isExempt(String path) {
        if (path == null) {
            return false;
        }
        for (String exempt : EXEMPT_PATHS) {
            if (path.equals(exempt)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            // Under MockMvc the servlet path is empty and the actual path lives in
            // the path info, so fall back to it instead of matching nothing.
            path = request.getPathInfo();
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path != null && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }
}
