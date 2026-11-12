package id.osprey.iamservice.domain.user;

import id.osprey.iamservice.domain.shared.PageResult;
import id.osprey.iamservice.domain.shared.PaginationRequest;

import java.util.Optional;

/**
 * Port untuk akses data pengguna.
 *
 * Port adalah kontrak yang dideklarasikan oleh domain dan diimplementasikan
 * oleh lapisan infrastruktur. Use case hanya mengenal antarmuka ini, bukan
 * Spring Data JPA.
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

    PageResult<User> findAll(PaginationRequest request);

    PageResult<User> search(String query, PaginationRequest request);
}
