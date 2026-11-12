package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.domain.auth.LoginPolicy;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Mencatat kegagalan login di transaksi terpisah.
 *
 * Kenapa transaksi terpisah:
 *      - Saat login gagal, service utama akan melempar galat sehingga
 *        transaksinya dibatalkan. Kalau pencatatan kegagalan ikut di transaksi
 *        yang sama, jejaknya hilang dan akun tidak pernah terkunci.
 *      - Transaksi baru membuat pencatatan ini berdiri sendiri dan tetap
 *        tersimpan meski transaksi utama dibatalkan.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final LoginPolicy loginPolicy;

    public LoginAttemptService(UserRepository userRepository, LoginPolicy loginPolicy) {
        this.userRepository = userRepository;
        this.loginPolicy = loginPolicy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.registerFailedLogin(loginPolicy.maxFailedAttempts(), loginPolicy.lockDuration(), Instant.now());
            userRepository.save(user);
        });
    }
}
