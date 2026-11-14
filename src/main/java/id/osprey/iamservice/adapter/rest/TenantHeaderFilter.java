package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.tenant.TenantResolver;
import id.osprey.iamservice.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter HTTP yang mengekstrak header X-Organization-ID dan menyetel
 * TenantContext sebelum request diproses. Konteks dibersihkan setelah
 * response selesai agar tidak bocor antar request yang share thread.
 *
 * Perilaku menurut ADR-0018 dan BUG-TENANT-001:
 *      - /v1/auth/login dan /v1/auth/refresh wajib membawa header.
 *        Header yang hilang maupun organisasi yang tidak dikenal dijawab
 *        401 Unauthorized.
 *      - Endpoint tenant-scoped lainnya (logout, daftar sesi, pencabutan
 *        sesi): header yang hilang tetap 400 Bad Request; organisasi yang
 *        tidak dikenal dijawab 401.
 *      - Header yang dikirim tetapi tidak dikenal di path mana pun dijawab
 *        401: diam-diam mengabaikannya sama dengan fallback ke tenant
 *        default, yang merupakan BUG-TENANT-001.
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_ORGANIZATION_ID = "X-Organization-ID";

    private static final String[] TENANT_REQUIRED_PATHS = {
            "/v1/auth/logout",
            "/v1/auth/sessions",
            "/v1/auth/sessions/"
    };

    private static final String[] TENANT_REQUIRED_UNAUTHORIZED_PATHS = {
            "/v1/auth/login",
            "/v1/auth/refresh"
    };

    private final TenantResolver tenantResolver;

    public TenantHeaderFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            // Under MockMvc the servlet path is empty and the actual path lives in
            // the path info, so fall back to it instead of matching nothing.
            path = request.getPathInfo();
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String organizationId = request.getHeader(HEADER_ORGANIZATION_ID);

        boolean unauthorizedPath = matchesAny(path, TENANT_REQUIRED_UNAUTHORIZED_PATHS);
        boolean tenantRequired = unauthorizedPath || matchesAny(path, TENANT_REQUIRED_PATHS);

        TenantResolver.Resolution resolution = tenantResolver.resolve(organizationId);

        if (resolution == TenantResolver.Resolution.MISSING) {
            if (unauthorizedPath) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Missing X-Organization-ID header");
                return;
            }
            if (tenantRequired) {
                reject(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Missing X-Organization-ID header");
                return;
            }
        } else if (resolution == TenantResolver.Resolution.UNKNOWN) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unknown organization");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean matchesAny(String path, String[] prefixes) {
        if (path == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
