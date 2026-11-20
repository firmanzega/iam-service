package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param username login name of the first ADMIN
 * @param email    email of the first ADMIN
 * @param password raw password, checked by PasswordPolicy in the use case
 */
public record BootstrapAdminRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 72) String password) {
}
