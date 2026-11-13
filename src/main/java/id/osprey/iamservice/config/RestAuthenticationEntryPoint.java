package id.osprey.iamservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Memberi tahu pemanggil ketika permintaan ditolak karena tidak punya token
 * atau punya token yang tidak valid.
 *
 * Kenapa tidak memakai halaman login bawaan Spring Security:
 *      - API ini tidak berbasis browser. Pemanggil yang wajar adalah aplikasi
 *        lain atau skrip, dan mereka butuh status serta body JSON, bukan
 *        halaman HTML.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final SecurityErrorWriter errorWriter;
    private final SecurityEventRecorder securityEventRecorder;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper,
                                        SecurityEventRecorder securityEventRecorder) {
        this.errorWriter = new SecurityErrorWriter(objectMapper);
        this.securityEventRecorder = securityEventRecorder;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.debug("Unauthorized access on {}: {}", request.getRequestURI(), authException.getMessage());

        // Alasan dibedakan berdasarkan ada atau tidaknya header Authorization,
        // bukan berdasarkan kelas exception pustaka. Nilainya dipakai sebagai
        // kunci agregat, jadi harus berupa kode pendek yang jumlahnya terbatas.
        String reason = request.getHeader(HttpHeaders.AUTHORIZATION) == null
                ? "missing_credentials"
                : "invalid_token";

        securityEventRecorder.recordUnauthorized(request, reason);

        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
