package id.osprey.iamservice.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Konfigurasi keamanan API.
 *
 * Keputusan desain:
 *      - Autentikasi berbasis token JWT yang dibawa lewat header Authorization.
 *        Karena token tidak disimpan di server, tidak ada sesi yang perlu
 *        disimpan, sehingga aplikasi tetap bisa diskalakan tanpa state.
 *      - CSRF dinonaktifkan. Proteksi CSRF hanya relevan bila kredensial
 *        browser otomatis ikut pada setiap permintaan. Di sini kredensial
 *        ditambahkan manual oleh klien pada header Authorization, jadi permintaan
 *        lintas situs tidak bisa dibuat tanpa mengetahui tokennya.
 *      - CORS memakai daftar origin eksplisit, bukan tanda bintang, karena
 *        permintaan dari origin lain memang harus ditolak.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final IamProperties properties;

    public SecurityConfig(IamProperties properties) {
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             JwtAuthenticationConverter jwtAuthenticationConverter,
                                             RestAuthenticationEntryPoint authenticationEntryPoint,
                                             RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoint autentikasi harus bisa diakses tanpa token,
                        // karena itulah tujuan token tersebut dibuat.
                        .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers("/auth/logout").authenticated()
                        // Aset statis Swagger UI seperti berkas JavaScript dan
                        // CSS-nya dilayani dari path webjars. Kalau path ini
                        // diblokir, halamannya terbuka tapi tetap kosong putih
                        // karena skripnya tidak pernah termuat.
                        .requestMatchers("/webjars/**").permitAll()
                        // Deskripsi API dan konfigurasinya boleh dibuka supaya
                        // halaman Swagger UI bisa berfungsi utuh.
                        //
                        // Di profil produksi, springdoc dimatikan seluruhnya,
                        // jadi tidak ada deskripsi API yang terekspos. Lihat
                        // application-prod.yaml.
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        // Health check dibiarkan terbuka karena dipakai probe
                        // orquestasi. Isinya tidak sensitif karena detail
                        // komponen tidak ditampilkan.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Halaman Swagger UI beserta asetnya. Hanya tersedia
                        // di profil pengembangan, karena di produksi springdoc
                        // dimatikan seluruhnya.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // Pengguna boleh mengganti kata sandinya sendiri.
                        // Aturan siapa yang boleh melakukan apa diperiksa lebih
                        // lanjut di level method memakai @PreAuthorize.
                        .requestMatchers(HttpMethod.PUT, "/users/*/password").authenticated()
                        // Daftar pengguna hanya untuk ADMIN dan AUDITOR.
                        // Data pengguna tidak untuk semua pemanggil.
                        .requestMatchers(HttpMethod.GET, "/users").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/users/**", "/roles/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/audit-logs/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/roles", "/roles/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/roles/**").hasRole("ADMIN")
                        .anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        // Content Security Policy di sini tidak memakai
                        // default-src 'none', karena itu akan memblokir
                        // seluruh skrip, gaya, dan gambar Swagger UI sehingga
                        // halamannya terbuka tapi kosong putih.
                        //
                        // Alasan tidak memakai 'none' tetap aman:
                        //   - Balasan API berupa JSON, bukan HTML. Header
                        //     X-Content-Type-Options: nosniff sudah
                        //     mencegah browser menebak tipe datanya.
                        //   - Swagger UI dimatikan seluruhnya di profil
                        //     produksi, jadi tidak ada halaman HTML yang
                        //     terekspos ke internet.
                        //
                        // Yang tetap diblokir adalah sumber dari luar: tidak
                        // ada skrip, gaya, atau gambar yang diambil dari
                        // domain lain.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "font-src 'self' data:; "
                                        + "connect-src 'self'; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'self'"))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)));

        return http.build();
    }

    /**
     * Mengubah claim "roles" pada token menjadi GrantedAuthority berbentuk
     * ROLE_ADMIN, ROLE_USER, dan sejenisnya. Prefix ROLE_ wajib agar
     * hasRole("ADMIN") pada konfigurasi otorisasi bisa mengenali nilai itu.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Validasi penerbit mencegah token yang diterbitkan sistem lain
        // diterima di sini, meskipun tanda tangannya sah.
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));

        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Kunci HMAC harus panjangnya minimal 256 bit. String yang lebih pendek akan
     * ditolak oleh pustaka kriptografi, dan itu memang lebih baik daripada
     * diam-diam memakai kunci lemah.
     */
    private SecretKey secretKey() {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "iam.jwt.secret minimal 32 byte, sekarang hanya " + secret.length + " byte");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }
}
