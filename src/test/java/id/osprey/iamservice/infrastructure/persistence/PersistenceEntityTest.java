package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.audit.AuditLog;
import id.osprey.iamservice.domain.auth.RefreshToken;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji jembatan antara entitas database dan objek domain.
 *
 * Pemetaan ini terlihat sepele, tetapi justru di sinilah field bisa hilang
 * tanpa disadari. Setiap getter dan setiap arah pemetaan disentuh supaya
 * perubahan skema yang lupa diteruskan ke domain langsung terlihat.
 */
class PersistenceEntityTest {

    @Test
    void roleEntity_mapsToDomainAndComparesByName() {
        RoleJpaEntity admin = new RoleJpaEntity("ADMIN");
        RoleJpaEntity adminAgain = new RoleJpaEntity("ADMIN");

        assertEquals("ADMIN", admin.getName());
        assertEquals(admin, adminAgain);
        assertEquals(admin.hashCode(), adminAgain.hashCode());
        assertEquals(admin, admin);
        assertFalse(admin.equals("ADMIN"));

        Role domain = admin.toDomain();
        assertEquals("ADMIN", domain.getName());
        assertNull(domain.getId());
    }

    @Test
    void auditLogEntity_persistsAndMapsAllFields() {
        AuditLogJpaEntity entity = new AuditLogJpaEntity(1L, "USER_UPDATED", 2L, "detail");

        entity.onCreate();

        assertEquals(1L, entity.getActorUserId());
        assertEquals("USER_UPDATED", entity.getAction());
        assertEquals(2L, entity.getTargetUserId());
        assertEquals("detail", entity.getDetails());
        assertNotNull(entity.getCreatedAt());
        assertNull(entity.getId());

        AuditLog domain = entity.toDomain();
        assertEquals("USER_UPDATED", domain.getAction());
        assertEquals(2L, domain.getTargetUserId());
        assertEquals("detail", domain.getDetails());
        assertEquals(entity.getCreatedAt(), domain.getCreatedAt());
    }

    @Test
    void refreshTokenEntity_withoutUser_keepsUserNull() {
        Instant expiresAt = Instant.now().plusSeconds(60);
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity("hash", expiresAt);

        entity.onCreate();

        assertEquals("hash", entity.getTokenHash());
        assertEquals(expiresAt, entity.getExpiresAt());
        assertNotNull(entity.getCreatedAt());
        assertNull(entity.getRevokedAt());
        assertNull(entity.getId());
        assertNull(entity.getUser());

        RefreshToken domain = entity.toDomain();
        assertNull(domain.getUser());
        assertEquals("hash", domain.getTokenHash());
        assertFalse(domain.isRevoked());
    }

    @Test
    void refreshTokenEntity_withUser_mapsUserAndRevocation() {
        UserJpaEntity userEntity = new UserJpaEntity("budi", "budi@example.com", "hash-kata-sandi");
        Instant revokedAt = Instant.now();

        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity("token-hash", Instant.now().plusSeconds(60));
        entity.setUser(userEntity);
        entity.markRevokedAt(revokedAt);

        assertEquals(userEntity, entity.getUser());
        assertEquals(revokedAt, entity.getRevokedAt());

        RefreshToken domain = entity.toDomain();
        assertNotNull(domain.getUser());
        assertEquals("budi", domain.getUser().getUsername());
        assertTrue(domain.isRevoked());
    }

    @Test
    void userEntity_copiesDomainStateAndTimestamps() {
        User domain = new User(5L, "budi", "budi@example.com", "hash", false, 2,
                Instant.now().plusSeconds(300), null, null, 4L, Set.of(new Role(1L, "ADMIN")));

        UserJpaEntity entity = new UserJpaEntity("lama", "lama@example.com", "hash-lama");
        entity.applyFrom(domain);
        entity.onCreate();
        entity.onUpdate();

        assertEquals("budi", entity.getUsername());
        assertEquals("budi@example.com", entity.getEmail());
        assertEquals("hash", entity.getPasswordHash());
        assertFalse(entity.isActive());
        assertEquals(2, entity.getFailedLoginAttempts());
        assertEquals(domain.getLockedUntil(), entity.getLockedUntil());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void userEntity_mapsRolesAndMissingVersionToDomain() {
        UserJpaEntity entity = new UserJpaEntity("budi", "budi@example.com", "hash");
        entity.replaceRoles(new LinkedHashSet<>(Set.of(new RoleJpaEntity("ADMIN"))));

        assertEquals(1, entity.getRoles().size());

        User domain = entity.toDomain();
        assertEquals("budi", domain.getUsername());
        assertEquals(1, domain.getRoles().size());
        assertTrue(domain.getRoles().contains(new Role("ADMIN")));
        assertEquals(0L, domain.getVersion());
        assertNull(entity.getVersion());
    }
}
