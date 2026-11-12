package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adapter yang memenuhi port UserRepository memakai Spring Data JPA.
 *
 * Adapter ini satu-satunya tempat yang tahu bahwa User dipetakan ke tabel
 * users. Use case tidak pernah melihat entitas JPA.
 */
@Repository
public class UserPersistenceAdapter implements UserRepository {

    private final SpringDataUserRepository repository;
    private final SpringDataRoleRepository roleRepository;

    public UserPersistenceAdapter(SpringDataUserRepository repository,
                                  SpringDataRoleRepository roleRepository) {
        this.repository = repository;
        this.roleRepository = roleRepository;
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String username, String email) {
        return repository.findByUsernameOrEmail(username, email).map(UserJpaEntity::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = user.getId() == null
                ? new UserJpaEntity(user.getUsername(), user.getEmail(), user.getPasswordHash())
                : repository.findById(user.getId()).orElseThrow();

        entity.applyFrom(user);

        // Relasi role hanya ditulis kalau benar-benar berubah.
        // Kalau selalu ditulis, setiap perubahan biasa ikut menghapus dan
        // menyisipkan ulang baris user_roles. Pada update bersamaan, dua
        // thread saling menunggu baris yang sama dan akhirnya terjadi deadlock.
        if (!hasSameRoles(entity, user.getRoles())) {
            entity.replaceRoles(resolveRoles(user.getRoles()));
        }

        // saveAndFlush dipakai, bukan save. Alasannya urutan lock:
        //      - Pemeriksaan kunci asing saat insert ke audit_logs mengambil
        //        kunci KEY SHARE pada baris pengguna.
        //      - Kalau pembaruan pengguna baru dieksekusi saat transaksi
        //        committed, dua pembaruan bersamaan bisa saling menunggu:
        //        satu memegang KEY SHARE, yang lain butuh kunci UPDATE.
        //        Hasilnya deadlock.
        //      - Dengan memaksa flush sekarang, pembaruan pengguna lebih dulu
        //        mengunci barisnya, sehingga deteksi optimistic locking bekerja
        //        seperti seharusnya.
        try {
            return repository.saveAndFlush(entity).toDomain();
        } catch (OptimisticLockingFailureException ex) {
            // Galat lapisan persistence diterjemahkan menjadi galat domain.
            // Dengan begitu use case dan lapisan antarmuka tidak perlu tahu
            // jenis exception dari framework.
            throw new DomainException(DomainException.Reason.CONCURRENT_MODIFICATION,
                    "The resource was modified by someone else. Reload and try again");
        }
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(UserJpaEntity::toDomain);
    }

    @Override
    public Page<User> search(String query, Pageable pageable) {
        return repository.search(query, pageable).map(UserJpaEntity::toDomain);
    }

    /**
     * Membandingkan role yang sedang melekat dengan role yang diinginkan.
     */
    private boolean hasSameRoles(UserJpaEntity entity, Set<Role> roles) {
        Set<Long> current = entity.getRoles().stream()
                .map(RoleJpaEntity::getId)
                .collect(Collectors.toSet());

        Set<Long> wanted = roles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        return current.equals(wanted);
    }

    /**
     * Mengubah role domain menjadi entitas role yang sudah ada di database.
     *
     * Entitasnya diambil dengan findAllById, bukan getReferenceById. Proxy dari
     * getReferenceById hanya bisa dipakai selama sesinya masih terbuka, sedangkan
     * pemetaan bisa terjadi di luar sesi itu.
     */
    private Set<RoleJpaEntity> resolveRoles(Set<Role> roles) {
        if (roles.isEmpty()) {
            return Set.of();
        }

        Map<Long, RoleJpaEntity> byId = roleRepository
                .findAllById(roles.stream().map(Role::getId).toList())
                .stream()
                .collect(Collectors.toMap(RoleJpaEntity::getId, Function.identity()));

        Set<RoleJpaEntity> resolved = new LinkedHashSet<>();

        for (Role role : roles) {
            RoleJpaEntity entity = byId.get(role.getId());

            if (entity != null) {
                resolved.add(entity);
            }
        }

        return resolved;
    }
}
