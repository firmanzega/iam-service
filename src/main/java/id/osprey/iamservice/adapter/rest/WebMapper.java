package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.domain.shared.PaginationRequest;
import id.osprey.iamservice.domain.shared.SortSpec;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Penerjemah kecil dari tipe web ke tipe domain.
 *
 * Kenapa dipusatkan: beberapa controller perlu menerjemahkan hal yang sama.
 * Kalau salinannya menyimpang, misalnya satu controller lupa membaca arah
 * pengurutan, perbedaannya sulit terlihat saat review. Satu tempat juga
 * membuat perubahan aturan penerjemahan hanya disentuh sekali.
 */
final class WebMapper {

    private WebMapper() {
    }

    /**
     * Identitas pelaku perubahan, diambil dari claim "sub" pada token.
     */
    static Long actorId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }

    /**
     * Menerjemahkan pagination dari lapisan web menjadi permintaan domain.
     */
    static PaginationRequest toRequest(Pageable pageable) {
        return new PaginationRequest(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().stream()
                        .map(order -> new SortSpec(order.getProperty(), order.isDescending()))
                        .toList());
    }
}
