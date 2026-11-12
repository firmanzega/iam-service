package id.osprey.iamservice.config;

import id.osprey.iamservice.adapter.rest.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Menulis penolakan keamanan memakai bentuk JSON yang sama dengan
 * GlobalExceptionHandler, supaya klien hanya perlu memahami satu format error.
 */
final class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletRequest request,
               HttpServletResponse response,
               HttpStatus status,
               String message) throws IOException {

        ErrorResponse body = ErrorResponse.of(status, message, request.getRequestURI(), null);

        response.setStatus(status.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
