package id.osprey.iamservice.config;

import id.osprey.iamservice.domain.auth.LoginPolicy;
import id.osprey.iamservice.domain.auth.TokenSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Menyediakan nilai pengaturan domain dari konfigurasi aplikasi.
 *
 * Kenapa perlu jembatan ini:
 *      - Lapisan domain tidak boleh membaca binding konfigurasi Spring
 *        secara langsung, karena itu berarti domain terikat framework.
 *      - Nilai konfigurasi diterjemahkan menjadi objek domain yang sederhana
 *        dan tidak berubah bentuknya.
 */
@Configuration
public class DomainConfig {

    @Bean
    LoginPolicy loginPolicy(IamProperties properties) {
        IamProperties.Login login = properties.login();
        return new LoginPolicy(login.maxFailedAttempts(), login.lockDuration());
    }

    @Bean
    TokenSettings tokenSettings(IamProperties properties) {
        IamProperties.Jwt jwt = properties.jwt();
        return new TokenSettings(jwt.issuer(), jwt.accessTokenTtl(), jwt.refreshTokenTtl());
    }
}
