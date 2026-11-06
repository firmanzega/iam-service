package id.osprey.iamservice.user;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class UserConcurrencyTest {

    @Autowired
    private UserService userService;

    @RepeatedTest(10)
    void twoConcurrentPatches_onlyOneSucceeds() throws Exception {
        String suffix = String.valueOf(System.nanoTime());

        UserResponse created = userService.create(
                new CreateUserRequest("race" + suffix, "race" + suffix + "@mail.com", "password123"));

        Long id = created.id();
        Long version = created.version();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // CountDownLatch ready memastikan kedua thread sudah siap sebelum lomba dimulai.
        CountDownLatch ready = new CountDownLatch(threads);

        //CountDownLatch start adalah aba-aba. Kedua thread menunggu di start.await(), lalu dilepas bersamaan lewat start.countDown().
        CountDownLatch start = new CountDownLatch(1);

        //AtomicInteger dipakai karena dua thread menambah counter bersamaan.
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String email = "new" + i + "_" + suffix + "@mail.com";
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();                       // semua thread menunggu aba-aba
                try {
                    userService.patch(id, new PatchUserRequest(null, email, version));
                    success.incrementAndGet();
                    System.out.println(Thread.currentThread().getName() + " -> SUKSES");
                } catch (OptimisticLockingFailureException e) {
                    conflict.incrementAndGet();
                    System.out.println(Thread.currentThread().getName() + " -> KONFLIK: " + e.getClass().getSimpleName());
                }
                return null;
            }));
        }

        ready.await();       // tunggu kedua thread siap
        start.countDown();   // lepas bersamaan
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        assertEquals(1, success.get());
        assertEquals(1, conflict.get());
    }

}
