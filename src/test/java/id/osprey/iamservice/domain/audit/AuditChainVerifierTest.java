package id.osprey.iamservice.domain.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji pemeriksaan rantai tanpa database.
 *
 * Verifier sengaja murni supaya aturan keutuhan bisa diuji dengan data yang
 * dirancang, termasuk data rusak yang sulit dibuat lewat API.
 */
class AuditChainVerifierTest {

    private static final Instant WAKTU = Instant.parse("2026-01-01T10:00:00.123456Z");

    @Test
    void emptyChain_isValid() {
        AuditChainVerification result = AuditChainVerifier.verify(List.of());

        assertTrue(result.valid());
        assertEquals(0, result.checkedEntries());
        assertNull(result.firstBrokenEntryId());
        assertNull(result.reason());
    }

    @Test
    void completeChain_isValid() {
        AuditChainEntry first = entry(1L, null, "USER_CREATED", "satu");
        AuditChainEntry second = entry(2L, first.entryHash(), "USER_UPDATED", "dua");
        AuditChainEntry third = entry(3L, second.entryHash(), "ROLE_CREATED", "tiga");

        AuditChainVerification result = AuditChainVerifier.verify(List.of(third, first, second));

        assertTrue(result.valid());
        assertEquals(3, result.checkedEntries());
        assertNull(result.firstBrokenEntryId());
    }

    @Test
    void changedField_isReportedAsHashMismatch() {
        AuditChainEntry first = entry(1L, null, "USER_CREATED", "satu");
        AuditChainEntry second = entry(2L, first.entryHash(), "USER_UPDATED", "dua");
        AuditChainEntry tampered = new AuditChainEntry(
                second.id(), second.actorUserId(), second.action(), second.targetUserId(),
                "diubah setelah hash", second.createdAt(), second.prevHash(), second.entryHash());

        AuditChainVerification result = AuditChainVerifier.verify(List.of(first, tampered));

        assertFalse(result.valid());
        assertEquals(2L, result.firstBrokenEntryId());
        assertEquals(AuditChainVerifier.HASH_MISMATCH, result.reason());
    }

    @Test
    void deletedMiddleEntry_isReportedAsMissingPredecessor() {
        AuditChainEntry first = entry(1L, null, "USER_CREATED", "satu");
        AuditChainEntry second = entry(2L, first.entryHash(), "USER_UPDATED", "dua");
        AuditChainEntry third = entry(3L, second.entryHash(), "ROLE_CREATED", "tiga");

        AuditChainVerification result = AuditChainVerifier.verify(List.of(first, third));

        assertFalse(result.valid());
        assertEquals(3L, result.firstBrokenEntryId());
        assertEquals(AuditChainVerifier.MISSING_PREDECESSOR, result.reason());
    }

    @Test
    void twoStartingEntries_isReportedAsMultipleHeads() {
        AuditChainEntry first = entry(1L, null, "USER_CREATED", "satu");
        AuditChainEntry second = entry(2L, null, "USER_UPDATED", "dua");

        AuditChainVerification result = AuditChainVerifier.verify(List.of(first, second));

        assertFalse(result.valid());
        assertEquals(2L, result.firstBrokenEntryId());
        assertEquals(AuditChainVerifier.MULTIPLE_HEADS, result.reason());
    }

    @Test
    void firstBrokenEntry_isTheSmallestId() {
        AuditChainEntry first = entry(1L, null, "USER_CREATED", "satu");
        AuditChainEntry second = entry(2L, first.entryHash(), "USER_UPDATED", "dua");
        AuditChainEntry changedSecond = new AuditChainEntry(
                second.id(), second.actorUserId(), second.action(), second.targetUserId(),
                "diubah", second.createdAt(), second.prevHash(), second.entryHash());
        AuditChainEntry third = entry(3L, "hash-yang-tidak-ada", "ROLE_CREATED", "tiga");

        AuditChainVerification result = AuditChainVerifier.verify(List.of(first, changedSecond, third));

        assertFalse(result.valid());
        assertEquals(2L, result.firstBrokenEntryId());
        assertEquals(AuditChainVerifier.HASH_MISMATCH, result.reason());
    }

    /**
     * Membuat entri dengan hash yang benar, dihitung dari data yang diberikan.
     */
    private AuditChainEntry entry(Long id, String prevHash, String action, String details) {
        AuditChainEntry draft = new AuditChainEntry(
                id, 9L, action, null, details, WAKTU, prevHash, "");
        return new AuditChainEntry(
                id, 9L, action, null, details, WAKTU, prevHash, AuditChainVerifier.hashOf(draft));
    }
}
