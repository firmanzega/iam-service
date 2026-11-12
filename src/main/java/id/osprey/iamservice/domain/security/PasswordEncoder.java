package id.osprey.iamservice.domain.security;

/**
 * Port untuk mengubah kata sandi mentah menjadi hash dan untuk
 * memverifikasinya.
 *
 * Implementasi nyata memakai BCrypt dan berada di lapisan infrastruktur.
 * Domain hanya butuh dua operasi ini.
 */
public interface PasswordEncoder {

    /**
     * Mengubah kata sandi mentah menjadi hash.
     *
     * @param rawPassword kata sandi mentah
     * @return hash yang aman disimpan
     */
    String encode(String rawPassword);

    /**
     * Memeriksa apakah kata sandi mentah cocok dengan hash tersimpan.
     *
     * @param rawPassword kata sandi mentah
     * @param encodedPassword hash yang tersimpan
     * @return true kalau cocok
     */
    boolean matches(String rawPassword, String encodedPassword);
}
