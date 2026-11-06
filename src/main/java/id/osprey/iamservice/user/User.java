package id.osprey.iamservice.user;

import id.osprey.iamservice.role.Role;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /**
     * Kenapa constructor kosong harus ada?
     *      - Saat Hibernate memuat data dari database, ia membuat objek User kosong dulu lewat reflection, lalu mengisi field-fieldnya satu per satu.
     *          Karena itu JPA mewajibkan entity punya constructor tanpa parameter. Kalau tidak ada, Hibernate gagal dengan error seperti No default constructor for entity.
     * Kenapa tidak public? Constructor kosong public berarti kode mana pun bisa menulis:
     *      - User user = new User();
     * Kenapa tidak private?
     *      - Secara aturan JPA, constructor kosong boleh protected atau public, tidak boleh private. Alasan praktisnya:
     *          Hibernate kadang membuat proxy, yaitu subclass turunan dari User yang dipakai untuk lazy loading (misalnya saat relasi dimuat belakangan).
     *          Subclass hanya bisa memanggil constructor induk yang tidak private. Dengan private, proxy tidak bisa dibuat dan lazy loading bisa bermasalah.
     * Jadi, Hibernate tetap bisa memakainya, sementara kode aplikasi di luar package user dipaksa lewat constructor berparameter.
     */
    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Jangan buka pintu langsung ke field, sediakan method yang punya makna:
     *      - Kode terbaca sebagai niat bisnis. user.deactivate() langsung jelas maksudnya. user.setActive(false) harus ditebak: ini dinonaktifkan karena apa?
     *      - Aturan dijaga di satu tempat. Misalnya nanti ada aturan “admin terakhir tidak boleh dinonaktifkan” atau “setiap deactivate dicatat di audit log”.
     *          Dengan deactivate(), aturan itu cukup ditulis sekali di dalam method. Dengan setActive, kamu harus mengingat menulisnya di setiap tempat yang memanggil setter,
     *          dan satu saja yang terlewat berarti bug.
     *      - Field yang sensitif tidak bisa diubah sembarangan. Ini yang paling relevan buat kita:
     *          1. passwordHash: hanya boleh berubah lewat changePassword(hash). Nama parameternya newHash mengingatkan bahwa yang masuk harus hash BCrypt, bukan password mentah.
     *          2. version: tidak boleh punya setter sama sekali. Nilainya milik Hibernate. Kalau kode kita bisa mengubahnya, proteksi optimistic locking bisa dirusak.
     *          3. id: sama, tidak ada setter. Database yang menentukan.
     *
     */

    public void changeUsername(String newUsername) {
        this.username = newUsername;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void replaceRoles(Set<Role> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

}
