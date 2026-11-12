package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param currentPassword kata sandi lama, dipakai untuk memastikan yang
 *                        mengganti memang pemilik akun
 * @param newPassword kata sandi baru
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(max = 72) String newPassword) {
}
