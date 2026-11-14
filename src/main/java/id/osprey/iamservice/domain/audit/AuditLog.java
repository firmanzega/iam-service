package id.osprey.iamservice.domain.audit;

import java.time.Instant;

/**
 * Catatan jejak audit untuk perubahan penting pada sistem.
 *
 * Untuk layanan identitas dan akses, jejak audit bukan pelengkap. Perubahan
 * role, aktivasi akun, dan penggantian kata sandi harus bisa ditelusuri siapa
 * yang melakukan apa dan kapan.
 */
public class AuditLog {

    private final Long id;
    private final Long tenantId;
    private final Long actorUserId;
    private final String action;
    private final Long targetUserId;
    private final String details;
    private final Instant createdAt;

    protected AuditLog() {
        this(null, null, null, null, null, null, null);
    }

    public AuditLog(Long actorUserId, String action, Long targetUserId, String details) {
        this(null, null, actorUserId, action, targetUserId, details, Instant.now());
    }

    public AuditLog(Long actorUserId, Long tenantId, String action, Long targetUserId, String details) {
        this(null, tenantId, actorUserId, action, targetUserId, details, Instant.now());
    }

    public AuditLog(Long id, Long actorUserId, String action, Long targetUserId,
                    String details, Instant createdAt) {
        this(id, null, actorUserId, action, targetUserId, details, createdAt);
    }

    public AuditLog(Long id, Long tenantId, Long actorUserId, String action, Long targetUserId,
                    String details, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
