package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param username nama masuk pengguna
 * @param email alamat surel
 * @param password kata sandi mentah
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 100) @Email String email,
        @NotBlank @Size(max = 72) String password) {
}
