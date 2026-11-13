package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Memastikan permintaan yang jarang dipakai tetap tervalidasi.
 *
 * ResetPasswordRequest dan UpdateUserRequest tidak muncul di jalur utama
 * pengujian HTTP, tetapi keduanya adalah pintu masuk data. Aturan validasinya
 * harus tetap diuji supaya tidak diam-diam longgar.
 */
class RequestDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void resetPassword_accessorReturnsNewPassword() {
        assertEquals("Rahasia123", new ResetPasswordRequest("Rahasia123").newPassword());
    }

    @Test
    void resetPassword_blank_isRejected() {
        Set<ConstraintViolation<ResetPasswordRequest>> violations =
                validator.validate(new ResetPasswordRequest("  "));

        assertFalse(violations.isEmpty());
    }

    @Test
    void resetPassword_tooLong_isRejected() {
        Set<ConstraintViolation<ResetPasswordRequest>> violations =
                validator.validate(new ResetPasswordRequest("a".repeat(73)));

        assertFalse(violations.isEmpty());
    }

    @Test
    void updateUser_accessorsReturnValues() {
        UpdateUserRequest request = new UpdateUserRequest("budi", "budi@example.com", 3L);

        assertEquals("budi", request.username());
        assertEquals("budi@example.com", request.email());
        assertEquals(3L, request.version());
    }

    @Test
    void updateUser_blankUsernameAndInvalidEmail_areRejected() {
        Set<ConstraintViolation<UpdateUserRequest>> violations =
                validator.validate(new UpdateUserRequest(" ", "bukan-surel", 1L));

        assertTrue(violations.size() >= 2);
    }

    @Test
    void updateUser_withoutVersion_isRejected() {
        Set<ConstraintViolation<UpdateUserRequest>> violations =
                validator.validate(new UpdateUserRequest("budi", "budi@example.com", null));

        assertEquals(1, violations.size());
    }
}
