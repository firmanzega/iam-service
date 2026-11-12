package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.application.auth.TokenPair;

import java.util.List;

/**
 * @param accessToken token untuk dipakai pada header Authorization
 * @param refreshToken token untuk ditukar menjadi token akses baru
 * @param tokenType jenis token, selalu "Bearer"
 * @param expiresIn sisa masa berlaku token akses dalam detik
 * @param roles role yang dimiliki pengguna
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        List<String> roles) {

    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresIn(), pair.roles());
    }
}
