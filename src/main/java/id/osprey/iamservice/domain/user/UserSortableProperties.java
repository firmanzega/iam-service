package id.osprey.iamservice.domain.user;

import java.util.Set;

/**
 * Daftar properti yang diizinkan sebagai kunci pengurutan pada daftar pengguna.
 *
 * Kenapa perlu dibatasi:
 *      - Tanpa daftar ini, klien bisa mengurutkan berdasarkan kolom
 *        passwordHash, sehingga hash kata sandi menjadi alat pengurutan bagi
 *        penyerang.
 *      - Properti yang tidak dikenal pun ditolak dengan pesan yang jelas,
 *        tanpa membocorkan nama kolom internal ke pemanggil.
 *
 * Nama yang dipakai adalah nama atribut pada entitas User, karena itulah yang
 * dipahami lapisan infrastruktur ketika menyusun klausul ORDER BY.
 */
public final class UserSortableProperties {

    public static final Set<String> ALLOWED = Set.of(
            "id",
            "username",
            "email",
            "active",
            "createdAt",
            "updatedAt");

    private UserSortableProperties() {
    }
}
