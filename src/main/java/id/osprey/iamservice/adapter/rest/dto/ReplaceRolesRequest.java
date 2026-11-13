package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * @param roleNames daftar role yang harus dimiliki pengguna
 * @param version nomor versi data saat dibaca pemanggil
 */
public record ReplaceRolesRequest(
        @NotNull Set<@NotBlank @Size(max = 50) String> roleNames,
        @NotNull Long version) {

    // Null sengaja diteruskan apa adanya supaya @NotNull tetap bisa
    // melaporkannya sebagai galat validasi, bukan galat saat deserialisasi.
    public ReplaceRolesRequest {
        roleNames = roleNames == null ? null : Set.copyOf(roleNames);
    }
}
