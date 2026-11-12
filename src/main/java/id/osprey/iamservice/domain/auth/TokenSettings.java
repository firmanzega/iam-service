package id.osprey.iamservice.domain.auth;

import java.time.Duration;

/**
 * Pengaturan penerbitan token.
 *
 * @param issuer identitas penerbit yang ditulis ke claim "iss". Token yang
 *                berasal dari penerbit lain harus ditolak.
 * @param accessTokenTtl masa berlaku token akses
 * @param refreshTokenTtl masa berlaku token refresh
 */
public record TokenSettings(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
