package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository Spring Data untuk entitas JPA pengguna.
 *
 * Ini bagian dari lapisan infrastruktur. Port yang dipakai use case ada di
 * id.osprey.iamservice.domain.user.UserRepository.
 */
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "roles")
    Page<UserJpaEntity> findAll(Pageable pageable);

    /**
     * Semua pengguna dalam satu tenant, untuk daftar pengguna yang difilter
     * tenant.
     */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from UserJpaEntity u where u.tenantId = :tenantId")
    Page<UserJpaEntity> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from UserJpaEntity u where u.id = :id and u.tenantId = :tenantId")
    Optional<UserJpaEntity> findById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * Mencari pengguna berdasarkan username atau email dalam satu tenant.
     *
     * Query eksplisit, bukan derived: parameter tenantId WAJIB masuk ke
     * WHERE. Versi derived sebelumnya menurunkan predicate
     * `(username = ? OR email = ?)` dan mengabaikan tenantId sama sekali
     * (terbukti dari NonUniqueResultException saat identitas yang sama
     * didaftarkan di dua tenant). Filter tenant adalah satu-satunya yang
     * menjaga login tidak melintas tenant.
     */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from UserJpaEntity u "
            + "where u.tenantId = :tenantId and (u.username = :username or u.email = :email)")
    Optional<UserJpaEntity> findByUsernameOrEmail(@Param("username") String username,
                                                  @Param("email") String email,
                                                  @Param("tenantId") Long tenantId);

    /**
     * Pencarian pada username dan email dalam satu tenant.
     *
     * Pola LIKE yang diawali wildcard tidak bisa dilayani indeks btree biasa.
     * Indeks yang membantu adalah indeks trigram pada lower(username) dan
     * lower(email), dibuat oleh migrasi V5.
     *
     * Filter tenant_id membatasi pencarian dalam satu tenant.
     */
    @EntityGraph(attributePaths = "roles")
    @Query("""
        SELECT u FROM UserJpaEntity u
        WHERE u.tenantId = :tenantId
          AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<UserJpaEntity> search(@Param("q") String q, @Param("tenantId") Long tenantId, Pageable pageable);
}
