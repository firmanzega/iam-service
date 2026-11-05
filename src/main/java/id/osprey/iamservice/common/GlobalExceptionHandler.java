package id.osprey.iamservice.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(e ->  details.put(e.getField(), e.getDefaultMessage()));

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                request.getRequestURI(),
                details);

        return ResponseEntity.status(response.status()).body(response);
    }

    /**
     * SQLState-nya:
     *      - 23505 untuk field unique
     *      - 23503 untuk foreign key
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {

        Throwable root = ex.getMostSpecificCause();
        String sqlState = root instanceof SQLException sql ? sql.getSQLState() : null;

        HttpStatus status;
        String message;

        if ("23505".equals(sqlState)) {
            status = HttpStatus.CONFLICT;
            message = "Username or email already exists";
        } else if ("23503".equals(sqlState)) {
            status = HttpStatus.CONFLICT;
            message = "Operation conflicts with related data";
        } else {
            log.error("Unexpected integrity violation on {}", request.getRequestURI(), ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred";
        }

        ErrorResponse response = ErrorResponse.of(status, message, request.getRequestURI(),null);

        return ResponseEntity.status(response.status()).body(response);

    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();

        ErrorResponse response = ErrorResponse.of(
                status,
                message,
                request.getRequestURI(),
                null);

        return ResponseEntity.status(response.status()).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.debug("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                request.getRequestURI(),
                null);

        return ResponseEntity.status(response.status()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {

        // Exception bawaan Spring (404, 405, 400 type mismatch, dll) hormati status aslinya
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            ErrorResponse response = ErrorResponse.of(
                    status, status.getReasonPhrase(), request.getRequestURI(), null);
            return ResponseEntity.status(response.status()).body(response);
        }

        log.error("Unhandled error in {}", request.getRequestURI(), ex);

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request.getRequestURI(),
                null);

        return ResponseEntity.status(response.status()).body(response);

    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(OptimisticLockingFailureException ex, HttpServletRequest request) {

        log.debug("Optimistic locking conflict on {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT,
                "The resource was modified by someone else. Reload and try again",
                request.getRequestURI(),
                null);

        return ResponseEntity.status(response.status()).body(response);
    }

}
