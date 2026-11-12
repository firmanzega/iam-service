package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.domain.shared.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * Bentuk halaman data yang dikembalikan ke pemanggil.
 *
 * Bentuknya disengaja: isi halaman dan informasi halaman dipisah ke dua
 * tingkat, supaya pemanggil tidak perlu menggali metadata di antara daftar
 * data.
 *
 * @param content isi halaman
 * @param page informasi halaman
 * @param <T> tipe isi
 */
public record PageResponse<T>(List<T> content, PageInfo page) {

    /**
     * @param size jumlah baris per halaman
     * @param number nomor halaman, dimulai dari nol
     * @param totalElements jumlah seluruh baris yang tersedia
     * @param totalPages jumlah halaman yang tersedia
     */
    public record PageInfo(int size, int number, long totalElements, int totalPages) {
    }

    /**
     * Menerjemahkan hasil halaman domain menjadi bentuk yang dikirim ke
     * pemanggil, sekaligus memetakan isi halamannya.
     */
    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        PageInfo info = new PageInfo(
                result.size(),
                result.page(),
                result.totalElements(),
                result.totalPages());

        return new PageResponse<>(result.content().stream().map(mapper).toList(), info);
    }
}
