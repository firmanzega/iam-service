package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.domain.shared.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/users");
    }

    @Test
    void validationFailure_returnsBadRequestWithFieldDetails() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "username", "must not be blank"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(mock(org.springframework.core.MethodParameter.class), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation Failed", response.getBody().message());
        assertEquals("must not be blank", response.getBody().details().get("username"));
    }

    @Test
    void uniqueViolationOnUsername_returnsConflictWithPreciseMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(violation("users_username_key"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Username already exists", response.getBody().message());
    }

    @Test
    void uniqueViolationOnEmail_returnsConflictWithPreciseMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(violation("users_email_key"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Email already exists", response.getBody().message());
    }

    @Test
    void uniqueViolationOnUnknownConstraint_returnsGenericMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(violation("entah_key"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("A record with the same unique value already exists", response.getBody().message());
    }

    @Test
    void uniqueViolationWithoutSqlState_cannotBeClassifiedSoItIsInternalServerError() {
        // Tanpa SQLException sebagai penyebab, kode status SQL tidak diketahui.
        // Handler tidak bisa menebak jenis pelanggaran, jadi aman diperlakukan
        // sebagai galat tak terduga.
        DataIntegrityViolationException ex = new DataIntegrityViolationException("tanpa nama batasan");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    void foreignKeyViolation_returnsConflict() {
        SQLException sql = new SQLException("keterangan", "23503");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("violasi", sql);

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Operation conflicts with related data", response.getBody().message());
    }

    @Test
    void unexpectedSqlState_returnsInternalServerError() {
        SQLException sql = new SQLException("keterangan", "99999");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("violasi", sql);

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    void malformedBody_returnsBadRequest() {
        ResponseEntity<ErrorResponse> response = handler.handleMalformedBody(
                new HttpMessageNotReadableException("bad", mock(org.springframework.http.HttpInputMessage.class)),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Malformed request body", response.getBody().message());
    }

    @Test
    void accessDenied_returnsForbidden() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new org.springframework.security.access.AccessDeniedException("ditolak"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().message());
    }

    @Test
    void authenticationFailure_returnsUnauthorized() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAuthentication(new org.springframework.security.core.AuthenticationException("tak diautentikasi") {
                }, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication required", response.getBody().message());
    }

    @Test
    void domainException_mapsReasonToStatus() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new DomainException(DomainException.Reason.USER_NOT_FOUND, "User not found"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("User not found", response.getBody().message());
    }

    @Test
    void domainException_withConcurrentModification_mapsToConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new DomainException(DomainException.Reason.CONCURRENT_MODIFICATION, "basi"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void domainException_withLockedAccount_mapsToLocked() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new DomainException(DomainException.Reason.ACCOUNT_LOCKED, "terkunci"), request);

        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
    }

    @Test
    void unhandledError_returnsInternalServerError() {
        ResponseEntity<ErrorResponse> response = handler.handleException(new IllegalStateException("bocor"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    void unhandledSpringWebError_keepsItsOwnStatus() {
        org.springframework.web.servlet.resource.NoResourceFoundException ex =
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/tidak-ada", "tidak ada");

        ResponseEntity<ErrorResponse> response = handler.handleException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    private DataIntegrityViolationException violation(String constraintName) {
        SQLException sql = new SQLException(
                "duplicate key value violates unique constraint \"" + constraintName + "\"", "23505");
        return new DataIntegrityViolationException("violasi", sql);
    }
}
