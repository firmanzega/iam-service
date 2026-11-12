package id.osprey.iamservice.domain.audit;

import java.util.Set;

/**
 * Daftar properti yang diizinkan sebagai kunci pengurutan pada daftar jejak audit.
 *
 * Kenapa dibatasi:
 *      - Kontrak API hanya menjanjikan pengurutan pada kolom yang bermakna.
 *        Properti yang tidak dikenal akan membuat query gagal di lapisan
 *        database, dan galat itu muncul sebagai 500, bukan 400.
 *      - Kolom detail tidak diizinkan karena isinya teks bebas dan tidak
 *        berguna sebagai kunci urutan.
 *
 * Nama yang dipakai adalah nama atribut pada entitas audit, karena itulah yang
 * dipahami lapisan infrastruktur saat menyusun klausul ORDER BY.
 */
public final class AuditLogSortableProperties {

    public static final Set<String> ALLOWED = Set.of(
            "id",
            "createdAt",
            "action",
            "actorUserId",
            "targetUserId");

    private AuditLogSortableProperties() {
    }
}
