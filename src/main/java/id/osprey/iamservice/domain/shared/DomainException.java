package id.osprey.iamservice.domain.shared;

/**
 * Galat yang lahir dari aturan bisnis.
 *
 * Kenapa ada kelas sendiri:
 *      - Lapisan use case tidak boleh tahu soal HTTP. Kalau use case
 *        melempar ResponseStatusException, dia sudah terikat pada web.
 *      - Lapisan antarmuka yang memetakan kategori galat ke status HTTP.
 *        Dengan begitu, aturan bisnis bisa dipakai ulang lewat transport lain.
 *
 * Kenapa pakai kategori:
 *      - Satu alasan galat dipetakan ke satu kategori. Lapisan antarmuka
 *        cukup memetakan kategori ke status HTTP, bukan setiap alasan satu
 *        per satu. Jadi menambah alasan baru cukup di satu tempat.
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

    public Category getCategory() {
        return reason.getCategory();
    }

    /**
     * Kelompok alasan galat menurut sifatnya.
     *
     * Kategori inilah yang dipetakan ke status HTTP di lapisan antarmuka.
     */
    public enum Category {

        /** Data yang diminta tidak ada. */
        NOT_FOUND,

        /** Identitas atau kredensial tidak dikenali. */
        UNAUTHORIZED,

        /** Identitas dikenali tetapi tidak berwenang. */
        FORBIDDEN,

        /** Isi permintaan tidak memenuhi aturan. */
        BAD_REQUEST,

        /** Data sudah diubah oleh pihak lain. */
        CONFLICT,

        /** Sumber daya sedang terkunci sementara. */
        LOCKED
    }

    /**
     * Daftar alasan galat yang berasal dari aturan bisnis.
     */
    public enum Reason {

        /** Pengguna yang dimaksud tidak ada. */
        USER_NOT_FOUND(Category.NOT_FOUND),

        /** Role yang dimaksud tidak ada. */
        ROLE_NOT_FOUND(Category.NOT_FOUND),

        /** Sesi yang dimaksud tidak ada atau bukan milik pemanggil. */
        SESSION_NOT_FOUND(Category.NOT_FOUND),

        /** Kredensial tidak dikenali. Pesannya sama untuk username yang salah
         *  dan kata sandi yang salah, supaya tidak membocorkan username mana
         *  yang terdaftar. */
        INVALID_CREDENTIALS(Category.UNAUTHORIZED),

        /** Akun sedang terkunci karena terlalu banyak kegagalan login. */
        ACCOUNT_LOCKED(Category.LOCKED),

        /** Akun dinonaktifkan, jadi tidak boleh dipakai. */
        ACCOUNT_INACTIVE(Category.FORBIDDEN),

        /** Kata sandi tidak memenuhi aturan kekuatan kata sandi. */
        INVALID_PASSWORD(Category.BAD_REQUEST),

        /** Kata sandi lama tidak cocok saat penggantian kata sandi. */
        CURRENT_PASSWORD_MISMATCH(Category.UNAUTHORIZED),

        /** Nama role yang diminta tidak dikenal. */
        UNKNOWN_ROLE(Category.BAD_REQUEST),

        /** Properti pengurutan yang diminta tidak diizinkan. */
        INVALID_SORT_PROPERTY(Category.BAD_REQUEST),

        /** Data sudah diubah oleh pihak lain, jadi versi yang dikirim basi. */
        CONCURRENT_MODIFICATION(Category.CONFLICT),

        /** Nilai unik sudah dipakai, misalnya username atau email. */
        DUPLICATE_RESOURCE(Category.CONFLICT),

        /** Role masih dipakai pengguna sehingga tidak boleh dihapus. */
        ROLE_IN_USE(Category.CONFLICT),

        /** Aksi tidak diizinkan pada sumber daya yang sedang dikunci. */
        FORBIDDEN_ACTION(Category.FORBIDDEN);

        private final Category category;

        Reason(Category category) {
            this.category = category;
        }

        public Category getCategory() {
            return category;
        }
    }
}
