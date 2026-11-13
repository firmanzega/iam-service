package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param identifier  username atau email
 * @param password    kata sandi mentah
 * @param deviceLabel label perangkat opsional, maksimal 100 karakter. Hanya
 *                    dipakai supaya pemilik akun bisa membedakan sesinya
 *                    sendiri; nilainya tidak dipercaya sebagai identitas
 */
public record LoginRequest(
        @NotBlank @Size(max = 100) String identifier,
        @NotBlank @Size(max = 72) String password,
        @Size(max = 100) String deviceLabel) {
}
