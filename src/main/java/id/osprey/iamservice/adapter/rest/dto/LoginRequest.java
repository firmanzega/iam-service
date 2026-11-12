package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param identifier username atau email
 * @param password kata sandi mentah
 */
public record LoginRequest(
        @NotBlank @Size(max = 100) String identifier,
        @NotBlank @Size(max = 72) String password) {
}
