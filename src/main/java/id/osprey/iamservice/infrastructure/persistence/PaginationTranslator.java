package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

/**
 * Mengubah tipe pagination milik domain menjadi tipe Spring Data, dan
 * sebaliknya.
 *
 * Adapter ini satu-satunya tempat yang tahu bahwa paginasi di database
 * memakai Pageable dan Page.
 */
final class PaginationTranslator {

    private PaginationTranslator() {
    }

    /**
     * Menerjemahkan permintaan pagination domain menjadi Pageable Spring Data.
     */
    static Pageable toPageable(PaginationRequest request) {
        Sort sort = Sort.by(request.sort().stream()
                .map(spec -> spec.descending()
                        ? Sort.Order.desc(spec.property())
                        : Sort.Order.asc(spec.property()))
                .toList());

        if (sort.isUnsorted()) {
            sort = Sort.unsorted();
        }

        return PageRequest.of(request.page(), request.size(), sort);
    }

    /**
     * Menerjemahkan hasil halaman Spring Data menjadi tipe domain.
     *
     * @param page hasil halaman dari Spring Data
     * @param mapper fungsi pemetaan tiap baris dari entitas database ke objek domain
     */
    static <S, T> PageResult<T> toPageResult(Page<S> page, Function<S, T> mapper) {
        return new PageResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
