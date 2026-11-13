package id.osprey.iamservice.domain.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji aturan sesi perangkat tanpa database.
 *
 * Sesi punya identitas sendiri karena satu sesi melewati banyak rotasi token
 * refresh. Aturan label dan masa berakhirnya diuji di sini supaya kesalahan
 * normalisasi tidak baru terlihat setelah masuk database.
 */
class SessionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);

    @Test
    void start_createsActiveSessionWithOwnIdentity() {
        Session session = Session.start(1L, "laptop", NOW);

        assertEquals(1L, session.getUserId());
        assertEquals("laptop", session.getDeviceLabel());
        assertEquals(NOW, session.getCreatedAt());
        assertEquals(NOW, session.getLastSeenAt());
        assertFalse(session.isRevoked());
        assertNotNull(session.getId());
    }

    @Test
    void start_generatesDifferentIdentityForEachSession() {
        UUID first = Session.start(1L, null, NOW).getId();
        UUID second = Session.start(1L, null, NOW).getId();

        assertFalse(first.equals(second), "setiap login perangkat harus punya id sesi sendiri");
    }

    @Test
    void start_trimsLabelAndTreatsBlankAsAbsent() {
        assertEquals("laptop kantor", Session.start(1L, "  laptop kantor  ", NOW).getDeviceLabel());
        assertNull(Session.start(1L, "   ", NOW).getDeviceLabel());
        assertNull(Session.start(1L, null, NOW).getDeviceLabel());
    }

    @Test
    void start_rejectsLabelLongerThanColumnWidth() {
        String tooLong = "a".repeat(Session.MAX_DEVICE_LABEL_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> Session.start(1L, tooLong, NOW));
    }

    @Test
    void touch_movesLastSeenForwardButNeverBackward() {
        Session session = Session.start(1L, "laptop", NOW);

        session.touch(NOW.plusSeconds(60));
        assertEquals(NOW.plusSeconds(60), session.getLastSeenAt());

        session.touch(NOW);
        assertEquals(NOW.plusSeconds(60), session.getLastSeenAt());
    }

    @Test
    void touch_doesNotReviveRevokedSession() {
        Session session = Session.start(1L, "laptop", NOW);
        session.revoke(NOW.plusSeconds(10));

        session.touch(NOW.plusSeconds(60));

        assertEquals(NOW, session.getLastSeenAt());
        assertTrue(session.isRevoked());
    }

    @Test
    void revoke_keepsTheFirstRevocationTime() {
        Session session = Session.start(1L, "laptop", NOW);

        session.revoke(NOW.plusSeconds(10));
        session.revoke(NOW.plusSeconds(60));

        assertEquals(NOW.plusSeconds(10), session.getRevokedAt());
    }

    @Test
    void isExpired_whenRevoked() {
        Session session = Session.start(1L, "laptop", NOW);
        session.revoke(NOW);

        assertTrue(session.isExpired(NOW, TTL));
    }

    @Test
    void isExpired_whenNotSeenForLongerThanRefreshTokenLifetime() {
        Session session = Session.start(1L, "laptop", NOW);

        assertFalse(session.isExpired(NOW.plus(TTL).minusSeconds(1), TTL));
        assertTrue(session.isExpired(NOW.plus(TTL), TTL));
    }

    @Test
    void belongsTo_onlyMatchesItsOwner() {
        Session session = Session.start(1L, "laptop", NOW);

        assertTrue(session.belongsTo(1L));
        assertFalse(session.belongsTo(2L));
    }
}
