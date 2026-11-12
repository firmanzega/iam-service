package id.osprey.iamservice.domain.auth;

import java.util.Optional;

/**
 * Port untuk penyimpanan token refresh.
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    /**
     * Mencabut seluruh token refresh milik seorang pengguna.
     *
     * @param userId identitas pengguna
     * @return jumlah token yang dicabut
     */
    long revokeAllForUser(Long userId);
}
