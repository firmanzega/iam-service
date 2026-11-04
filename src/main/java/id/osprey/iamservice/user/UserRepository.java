package id.osprey.iamservice.user;

import org.hibernate.annotations.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @EntityGraph(attributePaths = "roles")
    List<User> findAll();

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<User> findById(Long id);

    Optional<User> findByUsername(@Param("username") String username);

    boolean existsByEmail(String email);

    List<User> findByActiveTrue();

    /**
     * Sebelumnya -> @Query("select u from User u join u.roles r where r.name = :roleName")
     * Menggunakan Query Derivation (Penamaan Method) -> menggunakan konvensi penamaan method standar Spring Data JPA
     * yaitu Anda cukup meletakkan @EntityGraph di atas method tersebut tanpa anotasi @Query
     * Aturan Penamaan Method (Naming Convention & Traversal):
     *      - Awalan Query: Selalu gunakan awalan standar Spring Data JPA seperti findBy, readBy, queryBy, atau getBy.
     *      - Pemisah Underscore (_):
     *          * roles adalah nama field relasi di User.
     *          * Name adalah field di dalam Role.
     *          * Digabung menjadi: findByRoles_Name(...)
     *      - Fungsi _: Karakter underscore memberi tahu Spring Data JPA
     *          untuk melakukan traversal (penelusuran ke tabel relasi/join)
     *          alih-alih mencari satu kolom bernama rolesName di tabel User.
     */
    @EntityGraph(attributePaths = "roles")
    List<User> findByRoles_Name(@Param("roleName")  String roleName);


}
