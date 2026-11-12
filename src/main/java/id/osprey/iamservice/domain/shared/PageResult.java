package id.osprey.iamservice.domain.shared;

import java.util.List;

/**
 * Hasil satu halaman data.
 *
 * @param content isi halaman
 * @param page nomor halaman, dimulai dari nol
 * @param size jumlah baris per halaman
 * @param totalElements jumlah seluruh baris yang tersedia
 * @param totalPages jumlah halaman yang tersedia
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /**
     * Halaman kosong, dipakai saat pencarian tidak menemukan apa pun.
     */
    public static <T> PageResult<T> empty(PaginationRequest request) {
        return new PageResult<>(List.of(), request.page(), request.size(), 0, 0);
    }
}
