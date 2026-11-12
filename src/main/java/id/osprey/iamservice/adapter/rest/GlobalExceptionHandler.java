package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.domain.shared.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menerjemahkan galat menjadi bentuk JSON yang seragam.
 *
 * Lapisan inilah yang tahu pemetaan dari alasan galat bisnis ke status HTTP.
 * Use case tidak pernah menyentuh kode status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Pola untuk mengambil nama batasan unik dari pesan error PostgreSQL. */
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("constraint \"([^\"]+)\"");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                       HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));

        return respond(HttpStatus.BAD_REQUEST, "Validation Failed", request, details);
    }

    /**
     * Menangani galat yang lahir dari aturan bisnis.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        HttpStatus status = statusOf(ex.getReason());

        if (status.is5xxServerError()) {
            log.error("Domain error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.debug("Domain error on {}: {}", request.getRequestURI(), ex.getMessage());
        }

        return respond(status, ex.getMessage(), request, null);
    }

    /**
     * Pemetaan alasan galat bisnis ke status HTTP.
     */
    private HttpStatus statusOf(DomainException.Reason reason) {
        return switch (reason) {
            case USER_NOT_FOUND, ROLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_CREDENTIALS, CURRENT_PASSWORD_MISMATCH -> HttpStatus.UNAUTHORIZED;
            case ACCOUNT_LOCKED -> HttpStatus.LOCKED;
            case ACCOUNT_INACTIVE, FORBIDDEN_ACTION -> HttpStatus.FORBIDDEN;
            case INVALID_PASSWORD, UNKNOWN_ROLE, INVALID_SORT_PROPERTY -> HttpStatus.BAD_REQUEST;
            case CONCURRENT_MODIFICATION, DUPLICATE_RESOURCE -> HttpStatus.CONFLICT;
        };
    }

    /**
     * Menangani pelanggaran batasan database.
     *
     * SQLState-nya:
     *      - 23505 untuk field unique
     *      - 23503 untuk foreign key
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                        HttpServletRequest request) {

        Throwable root = ex.getMostSpecificCause();
        String sqlState = root instanceof SQLException sql ? sql.getSQLState() : null;

        HttpStatus status;
        String message;

        if ("23505".equals(sqlState)) {
            status = HttpStatus.CONFLICT;
            message = uniqueViolationMessage(root);
        } else if ("23503".equals(sqlState)) {
            status = HttpStatus.CONFLICT;
            message = "Operation conflicts with related data";
        } else {
            log.error("Unexpected integrity violation on {}", request.getRequestURI(), ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred";
        }

        return respond(status, message, request, null);
    }

    /**
     * Menyusun pesan konflik yang menyebut bagian data mana yang bentrok.
     *
     * Kenapa bukan satu pesan umum untuk semua pelanggaran batasan unik:
     *      - Pesan umum membuat pemanggil tidak tahu bagian mana dari
     *        permintaannya yang perlu diperbaiki.
     *      - Nama batasan di database sudah memberi tahu bagian itu, jadi
     *        informasinya tidak perlu disembunyikan. Yang disembunyikan
     *        hanyalah isi nilainya.
     */
    private static String uniqueViolationMessage(Throwable root) {
        String constraint = extractConstraintName(root);

        if (constraint == null) {
            return "A record with the same unique value already exists";
        }

        return switch (constraint) {
            case "users_username_key" -> "Username already exists";
            case "users_email_key" -> "Email already exists";
            case "refresh_tokens_token_hash_key" -> "Token already exists";
            case "roles_name_key" -> "Role name already exists";
            default -> "A record with the same unique value already exists";
        };
    }

    /**
     * Mengambil nama batasan unik dari pesan error PostgreSQL.
     *
     * Contoh pesan: duplicate key value violates unique constraint "users_username_key"
     */
    private static String extractConstraintName(Throwable root) {
        if (root == null) {
            return null;
        }

        Matcher matcher = CONSTRAINT_PATTERN.matcher(root.getMessage());
        return matcher.find() ? matcher.group(1) : null;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {

        log.debug("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());

        return respond(HttpStatus.BAD_REQUEST, "Malformed request body", request, null);
    }

    /**
     * Menangani penolakan karena hak akses tidak cukup.
     *
     * Kenapa perlu handler sendiri:
     *      - Spring Security memang punya AccessDeniedHandler, tetapi ia berada
     *        di luar lapisan MVC. Exception dari annotation @PreAuthorize
     *        dilempar saat method controller dipanggil, yaitu di dalam
     *        DispatcherServlet, sehingga lebih dulu ditangkap di sini.
     *      - Kalau tidak ditangani khusus, penolakan yang semestinya 403
     *        akan terbaca sebagai galat server 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {

        log.debug("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());

        return respond(HttpStatus.FORBIDDEN, "Access denied", request, null);
    }

    /**
     * Menangani permintaan yang tidak punya kredensial yang bisa dipakai.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {

        log.debug("Unauthenticated access on {}: {}", request.getRequestURI(), ex.getMessage());

        return respond(HttpStatus.UNAUTHORIZED, "Authentication required", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {

        // Exception bawaan Spring (404, 405, 400 type mismatch, dll) hormati status aslinya
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            return respond(status, status.getReasonPhrase(), request, null);
        }

        log.error("Unhandled error in {}", request.getRequestURI(), ex);

        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message,
                                                  HttpServletRequest request, Map<String, String> details) {
        ErrorResponse response = ErrorResponse.of(
                status,
                message,
                request.getRequestURI(),
                details);

        return ResponseEntity.status(status).body(response);
    }
}
