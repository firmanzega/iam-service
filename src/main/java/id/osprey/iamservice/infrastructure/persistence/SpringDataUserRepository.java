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

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<UserJpaEntity> findById(Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<UserJpaEntity> findByUsernameOrEmail(String username, String email);

    /**
     * Pencarian pada username dan email.
     *
     * Pola LIKE yang diawali wildcard tidak bisa dilayani indeks btree biasa.
     * Indeks yang membantu adalah indeks trigram pada lower(username) dan
     * lower(email), dibuat oleh migrasi V5.
     */
    @EntityGraph(attributePaths = "roles")
    @Query("""
        SELECT u FROM UserJpaEntity u
        WHERE (LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<UserJpaEntity> search(@Param("q") String q, Pageable pageable);
}
