package id.osprey.iamservice.application.user;

import id.osprey.iamservice.TestcontainersConfiguration;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Menguji bahwa dua PATCH yang berjalan bersamaan atas user yang sama hanya
 * menghasilkan satu pemenang, sementara yang lain ditolak dengan konflik versi.
 *
 * Kenapa test ini memakai @SpringBootTest dan bukan @DataJpaTest:
 *      - Skenarionya butuh dua koneksi database yang benar-benar berbeda.
 *        Test yang dibungkus @Transactional hanya memakai satu koneksi, sehingga
 *        kedua thread akan melihat data yang sama dan tidak terjadi konflik.
 *      - Konsekuensinya, data yang dibuat harus dibersihkan secara manual pada
 *        @AfterEach.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(UserConcurrencyTest.class);

    private static final String TEST_USERNAME_PREFIX = "race-test-";

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpTestUsers() {
        jdbcTemplate.update(
                "delete from user_roles where user_id in (select id from users where username like ?)",
                TEST_USERNAME_PREFIX + "%");
        jdbcTemplate.update("delete from users where username like ?", TEST_USERNAME_PREFIX + "%");
        TenantContext.clear();
    }

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(1L);
    }

    @RepeatedTest(10)
    void twoConcurrentPatches_onlyOneSucceeds() throws Exception {
        String suffix = String.valueOf(System.nanoTime());

        User created = userService.create(
                TEST_USERNAME_PREFIX + suffix,
                TEST_USERNAME_PREFIX + suffix + "@mail.com",
                "password123",
                null);

        Long id = created.getId();
        Long version = created.getVersion();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // CountDownLatch ready memastikan kedua thread sudah siap sebelum lomba dimulai.
        CountDownLatch ready = new CountDownLatch(threads);

        // CountDownLatch start adalah aba-aba. Kedua thread menunggu di start.await(),
        // lalu dilepas bersamaan lewat start.countDown().
        CountDownLatch start = new CountDownLatch(1);

        // AtomicInteger dipakai karena dua thread menambah counter bersamaan.
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String email = "new" + i + "_" + suffix + "@mail.com";
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();                       // semua thread menunggu aba-aba
                try {
                    userService.patch(id, null, email, version, null);
                    success.incrementAndGet();
                    log.debug("{} -> SUKSES", Thread.currentThread().getName());
                } catch (DomainException ex) {
                    if (ex.getReason() != DomainException.Reason.CONCURRENT_MODIFICATION) {
                        throw ex;
                    }
                    conflict.incrementAndGet();
                    log.debug("{} -> KONFLIK: {}", Thread.currentThread().getName(), ex.getReason());
                }
                return null;
            }));
        }

        ready.await();       // tunggu kedua thread siap
        start.countDown();   // lepas bersamaan
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, success.get());
        assertEquals(1, conflict.get());
    }
}
