package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.auth.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entitas JPA untuk tabel sessions.
 *
 * Identitas sesi dibuat di domain, bukan oleh database. Dengan begitu sesi
 * sudah punya id sebelum token ditandatangani, sehingga klaim session_id pada
 * token dan baris database selalu menunjuk nilai yang sama.
 */
@Entity
@Table(name = "sessions")
public class SessionJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserJpaEntity user;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "device_label", length = Session.MAX_DEVICE_LABEL_LENGTH)
    private String deviceLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SessionJpaEntity() {
    }

    public SessionJpaEntity(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public Session toDomain() {
        return new Session(id, user == null ? null : user.getId(), tenantId, deviceLabel,
                createdAt, lastSeenAt, revokedAt);
    }

    public UUID getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void setUser(UserJpaEntity user) {
        this.user = user;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
