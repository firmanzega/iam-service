package id.osprey.iamservice.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mengunci batas halaman di lapisan domain.
 *
 * Batas yang sama juga dijaga di lapisan web, tetapi domain tidak boleh
 * bergantung pada lapisan web untuk menolak nilai yang tidak masuk akal.
 */
class PaginationRequestTest {

    @Test
    void validValues_areAccepted() {
        PaginationRequest request = new PaginationRequest(0, PaginationRequest.MAX_SIZE, List.of());

        assertEquals(0, request.page());
        assertEquals(PaginationRequest.MAX_SIZE, request.size());
        assertTrue(request.sort().isEmpty());
    }

    @Test
    void negativePage_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaginationRequest(-1, 10, List.of()));
    }

    @Test
    void sizeBelowMinimum_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaginationRequest(0, 0, List.of()));
    }

    @Test
    void sizeAboveMaximum_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaginationRequest(0, PaginationRequest.MAX_SIZE + 1, List.of()));
    }

    @Test
    void nullSort_becomesEmptyImmutableList() {
        PaginationRequest request = new PaginationRequest(0, 10, null);

        assertTrue(request.sort().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> request.sort().add(new SortSpec("id", true)));
    }

    @Test
    void sort_isCopiedSoCallerCannotChangeItLater() {
        List<SortSpec> mutable = new ArrayList<>();
        mutable.add(new SortSpec("id", true));

        PaginationRequest request = new PaginationRequest(0, 10, mutable);
        mutable.clear();

        assertEquals(1, request.sort().size());
    }

    @Test
    void of_createsRequestWithoutSort() {
        PaginationRequest request = PaginationRequest.of(2, 5);

        assertEquals(2, request.page());
        assertEquals(5, request.size());
        assertTrue(request.sort().isEmpty());
    }
}
