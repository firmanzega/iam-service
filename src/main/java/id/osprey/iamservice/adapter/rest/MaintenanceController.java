package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.adapter.rest.dto.MaintenanceRequest;
import id.osprey.iamservice.application.maintenance.MaintenanceState;
import id.osprey.iamservice.application.maintenance.MaintenanceStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPS-only runtime toggle for the audit write-freeze.
 *
 * This endpoint is deliberately not part of the public API contract and is
 * documented in the deployment runbook instead. Toggle changes are written to
 * application logs and metrics, never to audit_logs.
 */
@Hidden
@RestController
@RequestMapping("/internal/maintenance")
public class MaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceController.class);

    private final MaintenanceState maintenanceState;
    private final MeterRegistry meterRegistry;

    public MaintenanceController(MaintenanceState maintenanceState, MeterRegistry meterRegistry) {
        this.maintenanceState = maintenanceState;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Returns the current maintenance status without changing it.
     */
    @GetMapping
    public MaintenanceStatus status() {
        return maintenanceState.snapshot();
    }

    /**
     * Enables the freeze. Only OPS may call this method.
     */
    @PostMapping("/enable")
    @PreAuthorize("hasRole('OPS')")
    public MaintenanceStatus enable(@AuthenticationPrincipal Jwt jwt,
                                    @RequestBody(required = false) MaintenanceRequest request) {
        String actor = jwt == null ? "unknown" : jwt.getSubject();
        String reason = request == null ? "unspecified" : request.normalizedReason();

        maintenanceState.enable(actor, reason);
        log.warn("Maintenance freeze ENABLED by {} reason={}", actor, reason);
        meterRegistry.counter("iam.maintenance.toggled", "action", "enable").increment();
        return maintenanceState.snapshot();
    }

    /**
     * Disables the freeze. Only OPS may call this method.
     */
    @PostMapping("/disable")
    @PreAuthorize("hasRole('OPS')")
    public MaintenanceStatus disable(@AuthenticationPrincipal Jwt jwt) {
        String actor = jwt == null ? "unknown" : jwt.getSubject();

        maintenanceState.disable();
        log.warn("Maintenance freeze DISABLED by {}", actor);
        meterRegistry.counter("iam.maintenance.toggled", "action", "disable").increment();
        return maintenanceState.snapshot();
    }
}
