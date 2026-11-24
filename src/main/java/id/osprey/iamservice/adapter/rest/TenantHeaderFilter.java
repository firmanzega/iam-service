package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.tenant.TenantResolver;
import id.osprey.iamservice.config.SecurityErrorWriter;
import id.osprey.iamservice.config.SecurityEventRecorder;
import id.osprey.iamservice.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

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
 *
 * Penolakan di jalur ini tercatat pada metrik dan agregat penolakan
 * (SecurityEventRecorder), sama seperti 401 dari entry point lain; tanpa
 * pencatatan itu lonjakan penolakan lewat jalur header menjadi tidak
 * terlihat di dashboard maupun di tabel agregat.
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_ORGANIZATION_ID = "X-Organization-ID";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_TENANT_ID = "tenantId";

    private static final String MISSING_REASON = "missing_organization";
    private static final String UNKNOWN_REASON = "unknown_organization";

    private static final String[] TENANT_REQUIRED_PATHS = {
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/logout",
            "/v1/auth/sessions",
            "/v1/auth/sessions/",
            "/v1/users",
            "/v1/users/",
            "/v1/audit-logs",
            "/v1/audit-logs/",
            "/v1/tenants",
            "/v1/tenants/"
    };

    private final TenantResolver tenantResolver;
    private final SecurityEventRecorder securityEventRecorder;
    private final SecurityErrorWriter errorWriter;

    public TenantHeaderFilter(TenantResolver tenantResolver,
                              SecurityEventRecorder securityEventRecorder,
                              ObjectMapper objectMapper) {
        this.tenantResolver = tenantResolver;
        this.securityEventRecorder = securityEventRecorder;
        this.errorWriter = new SecurityErrorWriter(objectMapper);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // A request id is created before any rejection so that even a 401 caused by
        // a missing header can be correlated in structured logs. It is deliberately
        // not named "traceId" to avoid colliding with OpenTelemetry MDC keys.
        MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString());

        try {
            String path = normalizePath(request);
            String organizationId = request.getHeader(HEADER_ORGANIZATION_ID);

            boolean tenantRequired = matchesAny(path, TENANT_REQUIRED_PATHS);

            TenantResolver.Resolution resolution = tenantResolver.resolve(organizationId);
            if (resolution == TenantResolver.Resolution.RESOLVED) {
                MDC.put(MDC_TENANT_ID, String.valueOf(TenantContext.getTenantId()));
            }

            if (resolution == TenantResolver.Resolution.MISSING) {
                if (tenantRequired) {
                    reject(request, response, MISSING_REASON, "Missing X-Organization-ID header");
                    return;
                }
            } else if (resolution == TenantResolver.Resolution.UNKNOWN) {
                reject(request, response, UNKNOWN_REASON, "Unknown organization");
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TENANT_ID);
            TenantContext.clear();
        }
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

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        String reason,
                        String message) throws IOException {
        securityEventRecorder.recordUnauthorized(request, reason);
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, message);
    }
}
