package id.osprey.iamservice.domain.auth;

import id.osprey.iamservice.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Token refresh yang pernah diterbitkan.
 *
 * Yang disimpan di sini hanya sidik jari SHA-256 dari token, bukan token
 * mentahnya. Lihat TokenIssuer untuk alasannya.
 *
 * sessionId boleh null untuk token yang terbit sebelum sesi perangkat ada.
 * Token seperti itu tetap bisa ditukar, lalu token penggantinya masuk ke sesi
 * baru.
 */
public class RefreshToken {

    private final Long id;
    private final User user;
    private final String tokenHash;
    private final UUID sessionId;
    private final Instant expiresAt;
    private Instant revokedAt;
    private final Instant createdAt;

    protected RefreshToken() {
        this(null, null, null, null, null, null, null);
    }

    public RefreshToken(User user, String tokenHash, UUID sessionId, Instant expiresAt) {
        this(null, user, tokenHash, sessionId, expiresAt, null, Instant.now());
    }

    public RefreshToken(Long id, User user, String tokenHash, UUID sessionId, Instant expiresAt,
                        Instant revokedAt, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.createdAt = createdAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getSessionId() {
        return sessionId;
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
}
