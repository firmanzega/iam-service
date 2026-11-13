package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.auth.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entitas JPA untuk tabel refresh_tokens.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserJpaEntity user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshTokenJpaEntity() {
    }

    public RefreshTokenJpaEntity(String tokenHash, UUID sessionId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Mengubah entitas database menjadi objek domain.
     */
    public RefreshToken toDomain() {
        return new RefreshToken(id, user == null ? null : user.toDomain(), tokenHash, sessionId,
                expiresAt, revokedAt, createdAt);
    }

    public Long getId() {
        return id;
    }

    public UserJpaEntity getUser() {
        return user;
    }

    public void setUser(UserJpaEntity user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Menandai waktu pencabutan token.
     */
    public void markRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
