package id.osprey.iamservice.domain.shared;

import java.util.List;

/**
 * Permintaan halaman dari pemanggil.
 *
 * Kenapa domain punya tipe sendiri:
 *      - supaya domain tidak mengimpor framework apa pun. Aturan arsitektur
 *        ini dijaga otomatis oleh ArchitectureLayerTest.
 *      - tipe ini adalah nilai murni, bukan bagian dari database maupun web.
 *
 * @param page nomor halaman, dimulai dari nol
 * @param size jumlah baris per halaman
 * @param sort daftar properti pengurutan
 */
public record PaginationRequest(int page, int size, List<SortSpec> sort) {

    /** Batas atas jumlah baris per halaman. */
    public static final int MAX_SIZE = 50;

    /** Batas bawah jumlah baris per halaman. */
    public static final int MIN_SIZE = 1;

    public PaginationRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }

        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        }

        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    /**
     * Permintaan halaman tanpa pengurutan tertentu.
     */
    public static PaginationRequest of(int page, int size) {
        return new PaginationRequest(page, size, List.of());
    }
}
