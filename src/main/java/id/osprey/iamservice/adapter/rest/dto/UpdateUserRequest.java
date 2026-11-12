package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param username nama masuk pengguna
 * @param email alamat surel
 * @param version nomor versi data saat dibaca pemanggil
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 100) @Email String email,
        @NotNull Long version) {
}
