package id.osprey.iamservice.config;

/**
 * Nama klaim token yang dipakai bersama oleh penerbit dan pembaca.
 *
 * Kenapa dipusatkan: klaim "session_id" ditulis oleh lapisan infrastruktur dan
 * dibaca oleh lapisan antarmuka. Kalau kedua sisi menuliskan nama itu sendiri,
 * salah satu bisa berubah tanpa yang lain ikut, dan sesi berhenti dikenali
 * tanpa ada yang gagal saat kompilasi.
 */
public final class TokenClaims {

    public static final String SESSION_ID = "session_id";

    private TokenClaims() {
    }
}
