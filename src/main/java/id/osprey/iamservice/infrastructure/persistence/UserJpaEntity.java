package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entitas JPA untuk tabel users.
 *
 * Pemetaan tabel dan kolom adalah urusan lapisan infrastruktur. Objek domain
 * yang setara ada di id.osprey.iamservice.domain.user.User.
 *
 * Kenapa dipisah:
 *      - Aturan bisnis seperti deactivate() dan registerFailedLogin() harus
 *        bisa diuji tanpa database dan tanpa JPA.
 *      - Pemetaan kolom, penguncian optimistis, dan relasi many-to-many adalah
 *        detail teknis yang tidak perlu bocor ke domain.
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 250)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleJpaEntity> roles = new LinkedHashSet<>();

    protected UserJpaEntity() {
    }

    public UserJpaEntity(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Menyalin keadaan dari objek domain ke entitas ini.
     *
     * Relasi role sengaja tidak ikut disalin di sini. Role yang sudah ada di
     * database harus disambungkan, bukan dibuat ulang. Penggantian relasi
     * ditangani oleh replaceRoles().
     *
     * @param domain objek domain yang menyimpan keadaan terbaru
     */
    public void applyFrom(User domain) {
        this.username = domain.getUsername();
        this.email = domain.getEmail();
        this.passwordHash = domain.getPasswordHash();
        this.active = domain.isActive();
        this.failedLoginAttempts = domain.getFailedLoginAttempts();
        this.lockedUntil = domain.getLockedUntil();
    }

    /**
     * Mengganti seluruh role milik pengguna.
     *
     * @param replacements entitas role baru
     */
    public void replaceRoles(Set<RoleJpaEntity> replacements) {
        this.roles.clear();
        this.roles.addAll(replacements);
    }

    /**
     * Mengubah entitas database menjadi objek domain.
     */
    public User toDomain() {
        Set<id.osprey.iamservice.domain.user.Role> domainRoles = roles.stream()
                .map(RoleJpaEntity::toDomain)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new User(id, username, email, passwordHash, active, failedLoginAttempts, lockedUntil,
                createdAt, updatedAt, version == null ? 0L : version, domainRoles);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Set<RoleJpaEntity> getRoles() {
        return roles;
    }
}
