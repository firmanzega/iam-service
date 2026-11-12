package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param newPassword kata sandi baru yang ditetapkan administrator
 */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 72) String newPassword) {
}
