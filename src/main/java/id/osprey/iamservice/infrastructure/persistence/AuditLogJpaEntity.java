package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.audit.AuditChainEntry;
import id.osprey.iamservice.domain.audit.AuditLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entitas JPA untuk tabel audit_logs.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Kedua kolom ini diisi trigger database. insertable dan updatable
    // dimatikan supaya Hibernate tidak ikut menulis null dan menimpa hasil
    // perhitungan trigger.
    @Column(name = "prev_hash", insertable = false, updatable = false)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false, insertable = false, updatable = false)
    private String entryHash;

    protected AuditLogJpaEntity() {
    }

    public AuditLogJpaEntity(Long actorUserId, String action, Long targetUserId, String details) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.details = details;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Mengubah entitas database menjadi objek domain.
     */
    public AuditLog toDomain() {
        return new AuditLog(id, actorUserId, action, targetUserId, details, createdAt);
    }

    /**
     * Mengubah entitas menjadi mata rantai untuk pemeriksaan keutuhan.
     */
    public AuditChainEntry toChainEntry() {
        return new AuditChainEntry(id, actorUserId, action, targetUserId, details, createdAt, prevHash, entryHash);
    }

    public Long getId() {
        return id;
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
