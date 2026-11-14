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
 * Perilaku (ADR-0018, BUG-TENANT-001, SEC-USERS-001) — satu aturan
 * seragam untuk seluruh endpoint tenant-aware:
 *      - Header yang hilang maupun organisasi yang tidak dikenal sama-sama
 *        dijawab 401 Unauthorized, di path mana pun, termasuk /v1/users/*.
 *      - Header yang dikirim tetapi tidak dikenal juga 401: diam-diam
 *        mengabaikannya sama dengan fallback ke tenant default, yang
 *        merupakan BUG-TENANT-001.
 * Perbedaan penyebab (missing vs unknown) tetap dibedakan lewat isi body
 * error, bukan lewat status code.
 *
 * /v1/audit-logs masuk daftar wajib header (BUG-TENANT-005): jejak audit
 * dipartisi per tenant (V9), dan tanpa header kueri dibatasi tenant null —
 * pemanggil menerima 200 dengan halaman kosong, bukan 401. Peran (role)
 * dikecualikan dengan sengaja karena role master bersifat global (V9).
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_ORGANIZATION_ID = "X-Organization-ID";

    private static final String[] TENANT_REQUIRED_PATHS = {
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/logout",
            "/v1/auth/sessions",
            "/v1/auth/sessions/",
            "/v1/users",
            "/v1/users/",
            "/v1/audit-logs",
            "/v1/audit-logs/"
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

        boolean tenantRequired = matchesAny(path, TENANT_REQUIRED_PATHS);

        TenantResolver.Resolution resolution = tenantResolver.resolve(organizationId);

        if (resolution == TenantResolver.Resolution.MISSING) {
            if (tenantRequired) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED,
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
