package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param username nama masuk baru, boleh tidak diisi
 * @param email alamat surel baru, boleh tidak diisi
 * @param version nomor versi data saat dibaca pemanggil
 */
public record PatchUserRequest(
        @Size(max = 50) @Pattern(regexp = "\\S+", message = "must not be blank") String username,
        @Email @Size(min = 1, max = 100) String email,
        @NotNull Long version) {
}
