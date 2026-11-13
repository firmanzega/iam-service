package id.osprey.iamservice.config;

import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Membuat satu akun administrator saat layanan pertama kali dijalankan.
 *
 * Kenapa perlu:
 *      - Endpoint pembuatan pengguna hanya boleh dipanggil oleh ADMIN. Tanpa
 *        akun administrator, tidak ada cara masuk ke sistem untuk pertama
 *        kali. Ini masalah chicken-and-egg yang harus diselesaikan.
 *
 * Kenapa aman:
 *      - Hanya aktif pada profil "dev" dan hanya kalau properti
 *        iam.dev-bootstrap.enabled bernilai true. Di produksi, kelas ini tidak
 *        pernah dimuat, sehingga tidak ada akun bawaan yang bisa dieksploitasi.
 *      - Kata sandi diambil dari environment variable IAM_DEV_ADMIN_PASSWORD.
 *        Kalau variabel itu kosong, dipakai nilai bawaan pengembangan supaya
 *        layanan tetap bisa dicoba tanpa konfigurasi tambahan. Nilai bawaan
 *        itu hanya hidup di profil dev dan wajib diganti sebelum dipakai
 *        bersama orang lain.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "iam.dev-bootstrap.enabled", havingValue = "true")
public class DevAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminBootstrap.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevAdminBootstrap(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = env("IAM_DEV_ADMIN_USERNAME", "admin");
        String email = env("IAM_DEV_ADMIN_EMAIL", "admin@iam.local");
        String password = env("IAM_DEV_ADMIN_PASSWORD", "Admin1234");

        if (userRepository.findByUsernameOrEmail(username, email).isPresent()) {
            log.info("Bootstrap administrator skipped, account {} already exists", username);
            return;
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ADMIN is missing, check the Flyway seed migration"));

        User admin = new User(username, email, passwordEncoder.encode(password));
        admin.replaceRoles(Set.of(adminRole));

        userRepository.save(admin);

        log.warn("Bootstrap administrator '{}' created. Change the password immediately.", username);
    }

    private String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
