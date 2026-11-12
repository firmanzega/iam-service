package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter yang memenuhi port RoleRepository memakai Spring Data JPA.
 */
@Repository
public class RolePersistenceAdapter implements RoleRepository {

    private final SpringDataRoleRepository repository;

    public RolePersistenceAdapter(SpringDataRoleRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Role> findById(Long id) {
        return repository.findById(id).map(RoleJpaEntity::toDomain);
    }

    @Override
    public Optional<Role> findByName(String name) {
        return repository.findByName(name).map(RoleJpaEntity::toDomain);
    }

    @Override
    public Set<Role> findByNameIn(Set<String> names) {
        Set<Role> roles = new LinkedHashSet<>();

        for (RoleJpaEntity entity : repository.findByNameIn(names)) {
            roles.add(entity.toDomain());
        }

        return roles;
    }

    @Override
    public Role save(Role role) {
        return repository.save(new RoleJpaEntity(role.getName())).toDomain();
    }

    @Override
    public void delete(Role role) {
        repository.deleteById(role.getId());
    }

    @Override
    public List<Role> findAll() {
        Set<Role> roles = new LinkedHashSet<>();

        for (RoleJpaEntity entity : repository.findAll()) {
            roles.add(entity.toDomain());
        }

        return List.copyOf(roles);
    }
}
