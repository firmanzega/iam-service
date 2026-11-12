package id.osprey.iamservice.domain.auth;

import id.osprey.iamservice.domain.user.User;

import java.util.List;

/**
 * Port untuk menerbitkan token sesi.
 *
 * Implementasi nyata menandatangani token JWT dan berada di lapisan
 * infrastruktur. Domain hanya butuh kemampuan meminta token dan menghitung
 * sidik jari token.
 */
public interface TokenIssuer {

    /**
     * Menerbitkan token akses yang membawa identitas dan role pengguna.
     */
    String issueAccessToken(User user);

    /**
     * Menerbitkan token refresh.
     */
    String issueRefreshToken(User user);

    /**
     * Menghitung sidik jari token mentah untuk disimpan di database.
     */
    String hashToken(String rawToken);

    /**
     * Daftar role pengguna, dipakai untuk ditampilkan pada hasil login.
     */
    List<String> roleNames(User user);
}
