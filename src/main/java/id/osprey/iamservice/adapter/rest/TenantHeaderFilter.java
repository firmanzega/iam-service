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
 * Endpoint yang memerlukan isolasi tenant (logout, daftar sesi, pencabutan
 * sesi) menolak tanpa header ini dengan 400 Bad Request.
 *
 * Endpoint terautentikasi lainnya (misalnya buat role, ganti kata sandi)
 * tidak memakai header ini. TenantId untuk mereka diambil dari klaim
 * tenant_id pada JWT oleh setiap service yang membutuhkannya, atau fallback
 * ke tenant default bila konteks kosong.
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_ORGANIZATION_ID = "X-Organization-ID";

    private static final String[] TENANT_REQUIRED_PATHS = {
            "/v1/auth/logout",
            "/v1/auth/sessions",
            "/v1/auth/sessions/"
    };

    private final TenantResolver tenantResolver;

    public TenantHeaderFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        String organizationId = request.getHeader(HEADER_ORGANIZATION_ID);

        boolean tenantRequired = isTenantRequired(path);
        if (tenantRequired && (organizationId == null || organizationId.isBlank())) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Missing X-Organization-ID header\"}");
            return;
        }

        tenantResolver.resolve(organizationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isTenantRequired(String path) {
        for (String prefix : TENANT_REQUIRED_PATHS) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
