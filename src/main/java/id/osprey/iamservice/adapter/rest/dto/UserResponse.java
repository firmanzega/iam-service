package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.User;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bentuk data pengguna yang dikembalikan ke pemanggil.
 *
 * @param id identitas pengguna
 * @param username nama masuk
 * @param email alamat surel
 * @param active apakah akun sedang aktif
 * @param version nomor versi untuk penguncian optimistis
 * @param roles nama role yang dimiliki pengguna
 */
public record UserResponse(Long id, String username, String email, boolean active, long version, Set<String> roles) {

    // Balasan pengguna memuat salinan role agar perubahan pada objek domain
    // setelah balasan terbentuk tidak ikut terlihat oleh pemanggil.
    public UserResponse {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isActive(),
                user.getVersion(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
    }
}
