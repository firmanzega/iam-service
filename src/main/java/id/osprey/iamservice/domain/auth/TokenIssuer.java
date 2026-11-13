package id.osprey.iamservice.domain.auth;

import id.osprey.iamservice.domain.user.User;

import java.util.List;
import java.util.UUID;

/**
 * Port untuk menerbitkan token sesi.
 *
 * Implementasi nyata menandatangani token JWT dan berada di lapisan
 * infrastruktur. Domain hanya butuh kemampuan meminta token dan menghitung
 * sidik jari token.
 */
public interface TokenIssuer {

    /**
     * Menerbitkan token akses yang membawa identitas, role pengguna, dan
     * penanda sesi.
     *
     * @param sessionId sesi pemilik token, boleh null untuk token lama
     */
    String issueAccessToken(User user, UUID sessionId);

    /**
     * Menerbitkan token refresh untuk sesi tertentu.
     *
     * @param sessionId sesi pemilik token, boleh null untuk token lama
     */
    String issueRefreshToken(User user, UUID sessionId);

    /**
     * Menghitung sidik jari token mentah untuk disimpan di database.
     */
    String hashToken(String rawToken);

    /**
     * Daftar role pengguna, dipakai untuk ditampilkan pada hasil login.
     */
    List<String> roleNames(User user);
}
