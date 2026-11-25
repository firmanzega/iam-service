package id.osprey.iamservice.application.maintenance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single runtime source of truth for audit write-freeze.
 *
 * Initialized from configuration so a restart defaults to normal operation.
 * The OPS toggle changes the same state without a restart.
 */
@Component
public class MaintenanceState {

    private final AtomicBoolean freeze;
    private final AtomicReference<Instant> enabledAt;
    private final AtomicReference<String> enabledBy;
    private final AtomicReference<String> reason;

    public MaintenanceState(@Value("${iam.maintenance.audit-freeze:false}") boolean initialFreeze) {
        this.freeze = new AtomicBoolean(initialFreeze);
        this.enabledAt = new AtomicReference<>(initialFreeze ? Instant.now() : null);
        this.enabledBy = new AtomicReference<>(initialFreeze ? "startup-env" : null);
        this.reason = new AtomicReference<>(initialFreeze ? "enabled via environment" : null);
    }

    /**
     * Returns whether audit writes are currently frozen.
     */
    public boolean isAuditFreeze() {
        return freeze.get();
    }

    /**
     * Enables the freeze if it is not already active.
     *
     * @param actor OPS actor from the JWT subject
     * @param why   human-readable reason recorded in the status snapshot
     */
    public void enable(String actor, String why) {
        if (freeze.compareAndSet(false, true)) {
            enabledAt.set(Instant.now());
            enabledBy.set(actor);
            reason.set(why);
        }
    }

    /**
     * Disables the freeze. The toggle endpoint must stay reachable while frozen.
     */
    public void disable() {
        freeze.set(false);
    }

    /**
     * Returns an immutable snapshot for the status endpoint.
     */
    public MaintenanceStatus snapshot() {
        return new MaintenanceStatus(freeze.get(), enabledAt.get(), enabledBy.get(), reason.get());
    }
}
