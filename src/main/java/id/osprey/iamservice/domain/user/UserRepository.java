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

    /**
     * Mengambil pengguna berdasarkan id dalam satu tenant. Tanpa predikat
     * tenant, pemanggil dapat membaca pengguna milik tenant lain hanya
     * dengan menebak id (integer berurutan).
     */
    Optional<User> findById(Long id, Long tenantId);

    /**
     * Mencari pengguna berdasarkan username atau email dalam satu tenant.
     *
     * @param username nilai yang dicocokkan dengan kolom username
     * @param email nilai yang dicocokkan dengan kolom email
     * @param tenantId tenant internal pemilik pengguna
     * @return pengguna yang cocok, atau kosong kalau tidak ada
     */
    Optional<User> findByUsernameOrEmail(String username, String email, Long tenantId);

    /**
     * Mencari ADMIN pertama milik satu tenant.
     *
     * Dipakai provisioning untuk menjamin idempotensi: tenant yang sudah
     * memiliki ADMIN tidak boleh diberi ADMIN kedua lewat endpoint bootstrap.
     *
     * @param tenantId tenant internal pemilik pengguna
     * @return ADMIN pertama, atau kosong bila belum ada
     */
    Optional<User> findFirstAdminByTenant(Long tenantId);

    User save(User user);

    PageResult<User> findAll(PaginationRequest request);

    PageResult<User> search(String query, PaginationRequest request);
}
