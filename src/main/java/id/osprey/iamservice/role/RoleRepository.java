package id.osprey.iamservice.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Set;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Set<Role> findByNameIn(Collection<String> names);
}
