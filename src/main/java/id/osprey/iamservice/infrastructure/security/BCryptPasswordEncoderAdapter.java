package id.osprey.iamservice.infrastructure.security;

import id.osprey.iamservice.domain.security.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implementasi port PasswordEncoder memakai BCrypt.
 *
 * BCrypt dipakai karena:
 *      - Saltnya dibuat otomatis dan ikut disimpan di dalam hash.
 *      - Penghitungannya lambat dengan sengaja, sehingga penyerang yang mencoba
 *        banyak kata sandi akan dihambat.
 *      - Panjangnya dibatasi 72 byte. Batas inilah alasan kata sandi lebih
 *        panjang dari itu ditolak oleh aturan kekuatan kata sandi.
 */
@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoder {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
