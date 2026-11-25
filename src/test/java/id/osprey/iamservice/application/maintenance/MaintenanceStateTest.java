package id.osprey.iamservice.application.maintenance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceStateTest {

    @Test
    void startsDisabledWhenConfigIsFalse() {
        MaintenanceState state = new MaintenanceState(false);

        assertFalse(state.isAuditFreeze());
        assertFalse(state.snapshot().auditFreeze());
    }

    @Test
    void startsEnabledWithStartupAttributionWhenConfigIsTrue() {
        MaintenanceState state = new MaintenanceState(true);

        assertTrue(state.isAuditFreeze());
        assertEquals("startup-env", state.snapshot().enabledBy());
        assertEquals("enabled via environment", state.snapshot().reason());
        assertNotNull(state.snapshot().enabledAt());
    }

    @Test
    void enableRecordsActorAndReason() {
        MaintenanceState state = new MaintenanceState(false);

        state.enable("ops-1", "hash backfill");

        assertTrue(state.isAuditFreeze());
        assertEquals("ops-1", state.snapshot().enabledBy());
        assertEquals("hash backfill", state.snapshot().reason());
    }

    @Test
    void disableClearsFlagButKeepsLastSnapshotMetadata() {
        MaintenanceState state = new MaintenanceState(false);
        state.enable("ops-1", "hash backfill");

        state.disable();

        assertFalse(state.isAuditFreeze());
        assertEquals("ops-1", state.snapshot().enabledBy());
    }

    @Test
    void enableIsIdempotentAndDoesNotOverwriteMetadata() {
        MaintenanceState state = new MaintenanceState(false);
        state.enable("ops-1", "first");

        state.enable("ops-2", "second");

        assertEquals("ops-1", state.snapshot().enabledBy());
        assertEquals("first", state.snapshot().reason());
    }
}
