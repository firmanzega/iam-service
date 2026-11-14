package id.osprey.iamservice.domain.user;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pengguna yang dikenali oleh sistem.
 *
 * Kenapa kelas ini tidak memakai anotasi JPA:
 *      - Aturan bisnis di sini harus bisa diuji tanpa database dan tanpa
 *        framework apa pun. Kalau entitas terikat JPA, setiap pengujian aturan
 *        bisnis ikut menarik dependensi framework.
 *      - Pemetaan ke tabel database adalah urusan lapisan infrastruktur, bukan
 *        urusan domain. Bagian itu ditangani oleh UserJpaEntity beserta
 *        pemetaannya.
 *
 * Kelas ini juga sengaja tidak punya setter untuk bagian yang sensitif.
 * Perubahan selalu lewat method bermakna, misalnya changeUsername() atau
 * deactivate(), supaya aturan di dalam method terkunci di satu tempat.
 */
public class User {

    private Long id;
    private Long tenantId;
    private String username;
    private String email;
    private String passwordHash;
    private boolean active;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long version;
    private final Set<Role> roles = new LinkedHashSet<>();

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this(null, null, username, email, passwordHash, true, 0, null, null, null, 0L, null);
    }

    /**
     * Membuat pengguna dengan tenant, untuk integrasi dan pengujian.
     *
     * @param tenantId tenant internal pemilik pengguna
     * @param username nama masuk
     * @param email alamat surel
     * @param passwordHash hash kata sandi
     */
    public User(Long tenantId, String username, String email, String passwordHash) {
        this(null, tenantId, username, email, passwordHash, true, 0, null, null, null, 0L, null);
    }

    /**
     * Menyusun ulang pengguna yang sudah tersimpan di database.
     *
     * @param id identitas pengguna
     * @param username nama masuk
     * @param email alamat surel
     * @param passwordHash hash kata sandi
     * @param active apakah akun sedang aktif
     * @param failedLoginAttempts jumlah kegagalan login beruntun
     * @param lockedUntil batas waktu sampai akun terkunci, null bila tidak terkunci
     * @param createdAt waktu pembuatan
     * @param updatedAt waktu perubahan terakhir
     * @param version nomor versi untuk penguncian optimistis
     * @param roles role yang dimiliki pengguna
     */
    public User(Long id, String username, String email, String passwordHash, boolean active,
                int failedLoginAttempts, Instant lockedUntil, LocalDateTime createdAt,
                LocalDateTime updatedAt, long version, Set<Role> roles) {
        this(id, null, username, email, passwordHash, active, failedLoginAttempts, lockedUntil,
                createdAt, updatedAt, version, roles);
    }

    /**
     * Menyusun ulang pengguna yang sudah tersimpan di database, termasuk tenant.
     *
     * @param id identitas pengguna
     * @param tenantId tenant internal pemilik pengguna
     * @param username nama masuk
     * @param email alamat surel
     * @param passwordHash hash kata sandi
     * @param active apakah akun sedang aktif
     * @param failedLoginAttempts jumlah kegagalan login beruntun
     * @param lockedUntil batas waktu sampai akun terkunci, null bila tidak terkunci
     * @param createdAt waktu pembuatan
     * @param updatedAt waktu perubahan terakhir
     * @param version nomor versi untuk penguncian optimistis
     * @param roles role yang dimiliki pengguna
     */
    public User(Long id, Long tenantId, String username, String email, String passwordHash,
                boolean active, int failedLoginAttempts, Instant lockedUntil,
                LocalDateTime createdAt, LocalDateTime updatedAt, long version, Set<Role> roles) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = active;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        if (roles != null) {
            this.roles.addAll(roles);
        }
    }

    public void changeUsername(String newUsername) {
        this.username = newUsername;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    /**
     * Mengganti hash kata sandi.
     *
     * @param newHash hash kata sandi yang sudah dihitung encoder, bukan kata
     *                sandi mentah. Algoritmanya urusan implementasi encoder.
     */
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
        if (newRoles != null) {
            this.roles.addAll(newRoles);
        }
    }

    /**
     * Mencatat satu kegagalan login.
     *
     * Kenapa akun dikunci setelah beberapa kali gagal:
     *      - Tanpa penguncian, penyerang bebas mencoba ribuan kata sandi per
     *        menit. Rate limiting per alamat IP saja tidak cukup karena
     *        penyerang berganti alamat IP.
     *      - Menghapus penguncian dengan sendirinya setelah jangka waktu tertentu
     *        membuat batas tebakan tetap berlaku tanpa perlu tindakan manual.
     *
     * @param maxAttempts jumlah kegagalan maksimum sebelum dikunci
     * @param lockDuration lama penguncian
     * @param now waktu sekarang
     */
    public void registerFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts++;

        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    /**
     * Menghapus riwayat kegagalan login karena login berhasil.
     */
    public void clearFailedLogins() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /**
     * Memeriksa apakah akun sedang terkunci pada waktu tertentu.
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
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

    public long getVersion() {
        return version;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
