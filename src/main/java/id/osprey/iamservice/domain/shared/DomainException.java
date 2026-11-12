package id.osprey.iamservice.domain.shared;

/**
 * Galat yang lahir dari aturan bisnis.
 *
 * Kenapa ada kelas sendiri:
 *      - Lapisan use case tidak boleh tahu soal HTTP. Kalau use case
 *        melempar ResponseStatusException, dia sudah terikat pada web.
 *      - Lapisan antarmuka yang memetakan alasan galat ke status HTTP.
 *        Dengan begitu, aturan bisnis bisa dipakai ulang lewat transport lain.
 *
 * Alasan galat sengaja berupa enum, bukan kelas terpisah, supaya daftar
 * kemungkinan tetap pendek dan mudah dibaca.
 */
public class DomainException extends RuntimeException {

    private final Reason reason;

    public DomainException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * Daftar alasan galat yang berasal dari aturan bisnis.
     */
    public enum Reason {

        /** Pengguna yang dimaksud tidak ada. */
        USER_NOT_FOUND,

        /** Role yang dimaksud tidak ada. */
        ROLE_NOT_FOUND,

        /** Kredensial tidak dikenali. Pesannya sama untuk username yang salah
         *  dan kata sandi yang salah, supaya tidak membocorkan username mana
         *  yang terdaftar. */
        INVALID_CREDENTIALS,

        /** Akun sedang terkunci karena terlalu banyak kegagalan login. */
        ACCOUNT_LOCKED,

        /** Akun dinonaktifkan, jadi tidak boleh dipakai. */
        ACCOUNT_INACTIVE,

        /** Kata sandi tidak memenuhi aturan kekuatan kata sandi. */
        INVALID_PASSWORD,

        /** Kata sandi lama tidak cocok saat penggantian kata sandi. */
        CURRENT_PASSWORD_MISMATCH,

        /** Nama role yang diminta tidak dikenal. */
        UNKNOWN_ROLE,

        /** Properti pengurutan yang diminta tidak diizinkan. */
        INVALID_SORT_PROPERTY,

        /** Data sudah diubah oleh pihak lain, jadi versi yang dikirim basi. */
        CONCURRENT_MODIFICATION,

        /** Nilai unik sudah dipakai, misalnya username atau email. */
        DUPLICATE_RESOURCE,

        /** Akun sedang dikunci sehingga token tidak bisa dicabut. */
        FORBIDDEN_ACTION
    }
}
