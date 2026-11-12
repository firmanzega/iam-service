package id.osprey.iamservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Memberi tahu pemanggil ketika identitas sudah dikenali tetapi role-nya tidak
 * cukup untuk melakukan aksi yang diminta.
 *
 * Perbedaan penting dengan RestAuthenticationEntryPoint:
 *      - 401 UNAUTHORIZED artinya belum terautentikasi sama sekali.
 *      - 403 FORBIDDEN artinya sudah terautentikasi, tetapi tidak berwenang.
 *      Pemisahan ini penting agar klien tahu harus memesan token baru
 *      atau memang butuh izin yang lebih besar.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final SecurityErrorWriter errorWriter;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.errorWriter = new SecurityErrorWriter(objectMapper);
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.debug("Access denied on {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

        errorWriter.write(request, response, HttpStatus.FORBIDDEN, "Access denied");
    }
}
