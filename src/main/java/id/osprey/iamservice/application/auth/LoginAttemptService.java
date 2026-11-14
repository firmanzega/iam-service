package id.osprey.iamservice.application.auth;

import id.osprey.iamservice.domain.auth.LoginPolicy;
import id.osprey.iamservice.domain.tenant.TenantContext;
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
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // Fail-closed: tanpa konteks tenant, akun yang dicurigai tidak
            // boleh dibiarkan tidak terlacak. Jalur login selalu punya
            // konteks karena filter menolak permintaan tanpa header.
            throw new IllegalStateException(
                    "TenantContext kosong di LoginAttemptService;"
                            + " TenantHeaderFilter harus menolak permintaan lebih dulu");
        }

        userRepository.findById(userId, tenantId).ifPresent(user -> {
            user.registerFailedLogin(loginPolicy.maxFailedAttempts(), loginPolicy.lockDuration(), Instant.now());
            userRepository.save(user);
        });
    }
}
