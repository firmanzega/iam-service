package id.osprey.iamservice.application.audit;

import id.osprey.iamservice.domain.audit.AuditChainEntry;
import id.osprey.iamservice.domain.audit.AuditChainVerification;
import id.osprey.iamservice.domain.audit.AuditChainVerifier;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import id.osprey.iamservice.domain.shared.SortSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    AuditLogRepository auditLogRepository;

    @InjectMocks
    AuditService auditService;

    @Test
    void findAll_withoutSort_appliesNewestFirstWithIdAsTiebreaker() {
        PaginationRequest request = PaginationRequest.of(0, 20);
        when(auditLogRepository.findAll(any())).thenReturn(PageResult.empty(request));

        auditService.findAll(request);

        PaginationRequest captured = captureRequest();
        assertEquals(List.of(
                new SortSpec("createdAt", true),
                new SortSpec("id", true)), captured.sort());
    }

    @Test
    void findAll_withExplicitSort_preservesCallerOrder() {
        List<SortSpec> requestedSort = List.of(new SortSpec("action", false));
        PaginationRequest request = new PaginationRequest(1, 10, requestedSort);
        when(auditLogRepository.findAll(any())).thenReturn(PageResult.empty(request));

        auditService.findAll(request);

        assertEquals(requestedSort, captureRequest().sort());
    }

    @Test
    void findAll_withUnknownSortProperty_isRejectedBeforeQuery() {
        PaginationRequest request = new PaginationRequest(0, 20,
                List.of(new SortSpec("details", true)));

        DomainException ex = assertThrows(DomainException.class, () -> auditService.findAll(request));

        assertEquals(DomainException.Reason.INVALID_SORT_PROPERTY, ex.getReason());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void verifyChain_withEmptyTable_returnsValidWithoutBrokenEntry() {
        when(auditLogRepository.findAllForChainVerification()).thenReturn(List.of());

        AuditChainVerification result = auditService.verifyChain();

        assertTrue(result.valid());
        assertEquals(0, result.checkedEntries());
        assertNull(result.firstBrokenEntryId());
        assertNull(result.reason());
    }

    @Test
    void verifyChain_withChangedEntry_returnsFirstBrokenEntryAndReason() {
        AuditChainEntry tampered = new AuditChainEntry(
                7L, 3L, "USER_UPDATED", null, "diubah setelah hash",
                Instant.parse("2026-01-01T10:00:00Z"), null, "hash-yang-tidak-cocok");
        when(auditLogRepository.findAllForChainVerification()).thenReturn(List.of(tampered));

        AuditChainVerification result = auditService.verifyChain();

        assertFalse(result.valid());
        assertEquals(1, result.checkedEntries());
        assertEquals(7L, result.firstBrokenEntryId());
        assertEquals(AuditChainVerifier.HASH_MISMATCH, result.reason());
    }

    private PaginationRequest captureRequest() {
        ArgumentCaptor<PaginationRequest> captor = ArgumentCaptor.forClass(PaginationRequest.class);
        verify(auditLogRepository).findAll(captor.capture());
        return captor.getValue();
    }
}
