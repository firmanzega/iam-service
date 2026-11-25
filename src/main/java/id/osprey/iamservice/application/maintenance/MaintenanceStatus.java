package id.osprey.iamservice.application.maintenance;

import java.time.Instant;

/**
 * Public status of the maintenance flag.
 *
 * @param auditFreeze whether audit writes are frozen
 * @param enabledAt   time the freeze was enabled, null when never enabled
 * @param enabledBy   OPS actor or startup-env, null when never enabled
 * @param reason      reason supplied by the actor, null when never enabled
 */
public record MaintenanceStatus(
        boolean auditFreeze,
        Instant enabledAt,
        String enabledBy,
        String reason) {
}
