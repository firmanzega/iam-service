package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.adapter.rest.dto.MaintenanceRequest;
import id.osprey.iamservice.application.maintenance.MaintenanceState;
import id.osprey.iamservice.application.maintenance.MaintenanceStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceControllerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Jwt opsToken = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("ops-1")
            .build();

    @AfterEach
    void closeRegistry() {
        meterRegistry.close();
    }

    @Test
    void statusBeforeAnyToggleIsDisabled() {
        MaintenanceController controller =
                new MaintenanceController(new MaintenanceState(false), meterRegistry);

        MaintenanceStatus status = controller.status();

        assertFalse(status.auditFreeze());
        assertNull(status.enabledBy());
    }

    @Test
    void enableRecordsActorAndNormalizedReason() {
        MaintenanceController controller =
                new MaintenanceController(new MaintenanceState(false), meterRegistry);

        MaintenanceStatus status = controller.enable(opsToken, new MaintenanceRequest("  hash backfill  "));

        assertTrue(status.auditFreeze());
        assertEquals("ops-1", status.enabledBy());
        assertEquals("hash backfill", status.reason());
        assertEquals(1, toggledCount("enable"));
    }

    @Test
    void enableWithoutBodyUsesUnspecifiedReason() {
        MaintenanceController controller =
                new MaintenanceController(new MaintenanceState(false), meterRegistry);

        MaintenanceStatus status = controller.enable(opsToken, null);

        assertEquals("unspecified", status.reason());
    }

    @Test
    void disableTurnsFreezeOffAndCountsToggle() {
        MaintenanceState state = new MaintenanceState(false);
        MaintenanceController controller = new MaintenanceController(state, meterRegistry);
        controller.enable(opsToken, new MaintenanceRequest("backfill"));

        MaintenanceStatus status = controller.disable(opsToken);

        assertFalse(status.auditFreeze());
        assertFalse(state.isAuditFreeze());
        assertEquals(1, toggledCount("enable"));
        assertEquals(1, toggledCount("disable"));
    }

    private double toggledCount(String action) {
        var counter = meterRegistry.find("iam.maintenance.toggled")
                .tag("action", action)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
