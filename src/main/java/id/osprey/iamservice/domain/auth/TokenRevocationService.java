package id.osprey.iamservice.domain.auth;

/**
 * Port untuk mencabut token milik seorang pengguna.
 *
 * Port ini ada supaya use case pengguna tidak perlu bergantung pada use case
 * autentikasi. Yang dibutuhkan memang hanya kemampuan mencabut sesi, bukan
 * seluruh alur autentikasi.
 */
public interface TokenRevocationService {

    /**
     * Mencabut seluruh token refresh milik seorang pengguna.
     *
     * Dipanggil setiap kali ada perubahan yang membuat sesi lama tidak valid,
     * misalnya penggantian kata sandi, penggantian role, atau akun dinonaktifkan.
     *
     * @param userId identitas pengguna
     */
    void revokeAllTokens(Long userId);
}
