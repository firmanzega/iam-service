package id.osprey.iamservice.application.security;

import id.osprey.iamservice.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"Rahasia123", "abc12345", "SandiPanjang99", "HurufDanAngka1"})
    void acceptablePassword_isAccepted(String password) {
        assertDoesNotThrow(() -> policy.validate(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pendek1", "tanpAngka", "12345678",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789012345678901"})
    void unacceptablePassword_isRejected(String password) {
        DomainException ex = assertThrows(DomainException.class,
                () -> policy.validate(password));

        assertEquals(DomainException.Reason.INVALID_PASSWORD, ex.getReason());
    }

    @Test
    void nullPassword_isRejected() {
        DomainException ex = assertThrows(DomainException.class,
                () -> policy.validate(null));

        assertEquals(DomainException.Reason.INVALID_PASSWORD, ex.getReason());
    }

    @Test
    void passwordLongerThanBcryptLimit_isRejected() {
        String tooLong = "A1" + "b".repeat(71);

        DomainException ex = assertThrows(DomainException.class,
                () -> policy.validate(tooLong));

        assertEquals(DomainException.Reason.INVALID_PASSWORD, ex.getReason());
    }

    @Test
    void rejectionMessage_explainsTheRule() {
        DomainException ex = assertThrows(DomainException.class,
                () -> policy.validate("abc"));

        assertTrue(ex.getMessage().contains("at least one letter and one digit"));
    }
}
