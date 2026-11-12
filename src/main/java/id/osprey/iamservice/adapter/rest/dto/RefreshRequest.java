package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param refreshToken token refresh yang akan ditukar
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
