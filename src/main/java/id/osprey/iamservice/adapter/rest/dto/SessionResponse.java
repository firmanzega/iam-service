package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.application.auth.SessionSummary;

import java.time.Instant;
import java.util.UUID;

/**
 * @param id          identitas sesi
 * @param deviceLabel label perangkat yang dikirim saat login, boleh null
 * @param createdAt   waktu sesi dimulai
 * @param lastSeenAt  waktu token refresh terakhir ditukar di sesi ini
 * @param expiresAt   perkiraan akhir sesi bila tidak dipakai lagi
 * @param current     true bila sesi ini yang sedang dipakai memanggil
 */
public record SessionResponse(
        UUID id,
        String deviceLabel,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current) {

    public static SessionResponse from(SessionSummary summary) {
        return new SessionResponse(
                summary.id(),
                summary.deviceLabel(),
                summary.createdAt(),
                summary.lastSeenAt(),
                summary.expiresAt(),
                summary.current());
    }
}
