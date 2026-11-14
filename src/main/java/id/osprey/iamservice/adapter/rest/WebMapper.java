package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.config.TokenClaims;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import id.osprey.iamservice.domain.shared.SortSpec;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

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
     * Sesi pemilik token, diambil dari claim "session_id".
     *
     * Token yang terbit sebelum fitur sesi ada tidak membawa klaim ini. Nilai
     * seperti itu dikembalikan sebagai null, bukan ditebak dari data lain,
     * supaya daftar perangkat tidak menandai sesi yang salah sebagai sesi
     * pemanggil.
     */
    static UUID sessionId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String value = jwt.getClaimAsString(TokenClaims.SESSION_ID);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Tenant pemilik token, diambil dari klaim "tenant_id".
     *
     * Token yang terbit sebelum multi-tenancy tidak membawa klaim ini. Nilai
     * seperti itu dikembalikan sebagai null, bukan ditebak dari data lain.
     */
    static Long tenantId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String value = jwt.getClaimAsString(TokenClaims.TENANT_ID);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
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
