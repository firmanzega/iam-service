package id.osprey.iamservice.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Port untuk akses data pengguna.
 *
 * Port adalah kontrak yang dideklarasikan oleh domain dan diimplementasikan oleh
 * lapisan infrastruktur. Use case hanya mengenal antarmuka ini, bukan Spring
 * Data JPA.
 *
 * Catatan tentang Pageable dan Page:
 *      - Keduanya hanya objek nilai, bukan bagian dari database.
 *      - Kalau suatu saat domain harus benar-benar bebas dari Spring Data,
 *        tinggal ganti tipe di sini dengan tipe milik domain sendiri.
 *        Perubahan itu terbatas pada berkas ini dan adapter-nya.
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    /**
     * Mencari pengguna berdasarkan username atau email.
     *
     * @param username nilai yang dicocokkan dengan kolom username
     * @param email nilai yang dicocokkan dengan kolom email
     * @return pengguna yang cocok, atau kosong kalau tidak ada
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    User save(User user);

    Page<User> findAll(Pageable pageable);

    Page<User> search(String query, Pageable pageable);
}
