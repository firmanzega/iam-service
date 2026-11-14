package id.osprey.iamservice.domain.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Sesi satu perangkat.
 *
 * Kenapa sesi punya identitas sendiri, bukan sekadar kumpulan token refresh:
 *      - Rotasi token refresh membuat token berganti setiap kali dipakai.
 *        Kalau sesi dihitung dari token, daftar perangkat akan berubah setiap
 *        kali pengguna membuka aplikasi.
 *      - Pencabutan satu perangkat harus mencakup seluruh token yang pernah
 *        terbit di sesi itu. Identitas sesi yang stabil membuat pencabutan
 *        tidak perlu menebak token mana yang termasuk.
 */
public class Session {

    /** Batas panjang label perangkat, sama dengan lebar kolom di database. */
    public static final int MAX_DEVICE_LABEL_LENGTH = 100;

    private final UUID id;
    private final Long userId;
    private final Long tenantId;
    private final String deviceLabel;
    private final Instant createdAt;
    private Instant lastSeenAt;
    private Instant revokedAt;

    public Session(UUID id, Long userId, String deviceLabel, Instant createdAt,
                   Instant lastSeenAt, Instant revokedAt) {
        this(id, userId, null, deviceLabel, createdAt, lastSeenAt, revokedAt);
    }

    public Session(UUID id, Long userId, Long tenantId, String deviceLabel, Instant createdAt,
                   Instant lastSeenAt, Instant revokedAt) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.deviceLabel = deviceLabel;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
        this.revokedAt = revokedAt;
    }

    /**
     * Membuat sesi baru yang langsung aktif.
     *
     * Label perangkat berasal dari pemanggil dan boleh kosong. Layanan tidak
     * menebaknya dari User-Agent: nilai itu dikendalikan klien, mudah dipalsukan,
     * dan tidak cukup dapat dipercaya untuk dijadikan identitas perangkat.
     *
     * @param tenantId tenant internal pemilik sesi ini
     */
    public static Session start(Long userId, Long tenantId, String deviceLabel, Instant now) {
        return new Session(UUID.randomUUID(), userId, tenantId, normalizeLabel(deviceLabel), now, now, null);
    }

    public void touch(Instant now) {
        if (revokedAt == null && now.isAfter(lastSeenAt)) {
            lastSeenAt = now;
        }
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Sesi dianggap berakhir kalau sudah dicabut atau tidak dipakai lebih lama
     * dari masa berlaku token refresh. Token refresh yang tidak ditukar dalam
     * rentang itu sudah kedaluwarsa, jadi sesinya tidak bisa dilanjutkan lagi.
     */
    public boolean isExpired(Instant now, Duration refreshTokenTtl) {
        return isRevoked() || !lastSeenAt.plus(refreshTokenTtl).isAfter(now);
    }

    public boolean belongsTo(Long ownerId) {
        return userId != null && userId.equals(ownerId);
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    private static String normalizeLabel(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.length() > MAX_DEVICE_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label perangkat maksimal " + MAX_DEVICE_LABEL_LENGTH + " karakter");
        }

        return trimmed;
    }
}
