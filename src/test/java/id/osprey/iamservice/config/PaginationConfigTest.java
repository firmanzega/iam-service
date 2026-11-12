package id.osprey.iamservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mengunci pagu ukuran halaman agar tidak bisa dilanggar tanpa disadari.
 *
 * Kenapa perlu test ini:
 *      - Dulu halaman dikunci lewat @EnableSpringDataWebSupport. Anotasi itu
 *        mendaftarkan resolver pagination sendiri sehingga nilai
 *        spring.data.web.pageable.max-page-size ikut terabaikan, dan klien
 *        bisa meminta ribuan baris sekaligus.
 *      - Sekarang pagu diambil dari properti spring.data.web.pageable.max-page-size.
 *        Test ini memastikan properti itu benar-benar terpakai. Kalau suatu saat
 *        muncul konfigurasi yang menimpa resolver lagi, test ini langsung gagal.
 */
@SpringBootTest
class PaginationConfigTest {

    private static final int EXPECTED_MAX_PAGE_SIZE = 50;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableResolver;

    @Test
    void requestedSize_isCappedByConfiguredMaxPageSize() throws Exception {
        Method probe = Probe.class.getDeclaredMethod("probe", Pageable.class);
        MethodParameter parameter = new MethodParameter(probe, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("page", "0");
        request.setParameter("size", "99999");

        Pageable pageable = pageableResolver.resolveArgument(
                parameter, null, new ServletWebRequest(request), null);

        assertEquals(EXPECTED_MAX_PAGE_SIZE, pageable.getPageSize());
    }

    @SuppressWarnings("unused")
    private static final class Probe {

        void probe(Pageable pageable) {
        }
    }
}
