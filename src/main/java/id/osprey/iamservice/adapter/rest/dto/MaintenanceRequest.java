package id.osprey.iamservice.adapter.rest.dto;

/**
 * @param reason human-readable reason recorded in the maintenance status
 */
public record MaintenanceRequest(String reason) {

    /**
     * Normalizes a blank reason so status snapshots stay informative.
     */
    public String normalizedReason() {
        return reason == null || reason.isBlank() ? "unspecified" : reason.trim();
    }
}
