package id.osprey.iamservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                        // Seluruh kontrak publik berada di bawah /v1. Awalan versi
                        // ini bagian dari kontrak, bukan hiasan: perubahan yang
                        // tidak kompatibel nanti diterbitkan sebagai /v2, dan
                        // pemanggil lama tidak terpaksa ikut berubah.
                        //
                        // Endpoint autentikasi harus bisa diakses tanpa token,
                        // karena itulah tujuan token tersebut dibuat.
                        .requestMatchers("/v1/auth/login", "/v1/auth/refresh").permitAll()
                        .requestMatchers("/v1/auth/logout").authenticated()
                        // Daftar dan pencabutan sesi selalu milik pemanggil.
                        // Tanpa aturan ini, permintaan akan jatuh ke
                        // anyRequest().hasRole("ADMIN") dan pengguna biasa
                        // tidak bisa melihat perangkatnya sendiri.
                        .requestMatchers("/v1/auth/sessions", "/v1/auth/sessions/**").authenticated()
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
                        // The Prometheus endpoint is scraped by an internal
                        // collector, and that collector cannot refresh a
                        // 15-minute access token. The exposure is acceptable
                        // only while the service cannot be reached from the
                        // public network; see docs/deployment.md.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Halaman Swagger UI beserta asetnya. Hanya tersedia
                        // di profil pengembangan, karena di produksi springdoc
                        // dimatikan seluruhnya.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // Pengguna boleh mengganti kata sandinya sendiri.
                        // Aturan siapa yang boleh melakukan apa diperiksa lebih
                        // lanjut di level method memakai @PreAuthorize.
                        .requestMatchers(HttpMethod.PUT, "/v1/users/*/password").authenticated()
                        // Daftar pengguna hanya untuk ADMIN dan AUDITOR.
                        // Data pengguna tidak untuk semua pemanggil.
                        .requestMatchers(HttpMethod.GET, "/v1/users").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/v1/users/**", "/v1/roles/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/audit-logs/verify").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/audit-logs/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/v1/roles", "/v1/roles/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/roles/**").hasRole("ADMIN")
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
    JwtEncoder jwtEncoder(JwtKeyRing keyRing) {
        return keyRing.encoder();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyRing keyRing) {
        return keyRing.decoder();
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
}
