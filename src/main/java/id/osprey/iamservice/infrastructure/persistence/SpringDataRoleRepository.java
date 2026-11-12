package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

/**
 * Repository Spring Data untuk entitas JPA role.
 */
public interface SpringDataRoleRepository extends JpaRepository<RoleJpaEntity, Long> {

    Optional<RoleJpaEntity> findByName(String name);

    Set<RoleJpaEntity> findByNameIn(Set<String> names);
}
