package id.osprey.iamservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Menyediakan PostgreSQL sekali pakai untuk seluruh pengujian integrasi.
 *
 * Kenapa tidak memakai database pengembangan:
 *      - Pengujian integrasi membuat dan menghapus data. Kalau database-nya
 *        sama dengan database pengembangan, data contoh bisa hilang dan hasil
 *        tes bergantung pada keadaan database itu.
 *      - Container baru menjamin skema dibangun ulang dari migrasi Flyway,
 *        sehingga perbedaan skema langsung terlihat.
 *
 * Kenapa container dibuat sekali untuk seluruh JVM:
 *      - Setiap kelas tes punya konteks Spring sendiri karena propertinya
 *        berbeda. Kalau container dibuat per kelas, biaya start berulang dan
 *        seluruh rangkaian tes menjadi jauh lebih lambat.
 *      - Container dimatikan otomatis oleh Testcontainers saat JVM selesai.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }
}
