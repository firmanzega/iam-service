package id.osprey.iamservice.application.security;

import id.osprey.iamservice.domain.shared.DomainException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Aturan kekuatan kata sandi.
 *
 * Kenapa aturan ini dipakai:
 *      - Panjang minimal 8 karakter adalah batas bawah yang lazim. Yang lebih
 *        penting adalah variasi jenis karakter, karena password pendek yang
 *        hanya satu jenis karakter mudah ditebak.
 *      - Batas atas 72 karakter berasal dari BCrypt. BCrypt hanya memakai 72
 *        byte pertama, sehingga karakter setelah itu diabaikan. Menerima
 *        password lebih panjang membuat orang menyangka passwordnya lebih kuat
 *        padahal tidak.
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private static final Pattern PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");

    /**
     * Memeriksa kata sandi mentah sebelum di-hash.
     *
     * @param rawPassword kata sandi dalam bentuk mentah
     * @throws DomainException bila tidak memenuhi aturan
     */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw invalid();
        }

        if (!PATTERN.matcher(rawPassword).matches()) {
            throw invalid();
        }
    }

    private DomainException invalid() {
        return new DomainException(DomainException.Reason.INVALID_PASSWORD,
                "Password must be " + MIN_LENGTH + " to " + MAX_LENGTH
                        + " characters long and contain at least one letter and one digit");
    }
}
