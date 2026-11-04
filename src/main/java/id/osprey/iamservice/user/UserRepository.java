package id.osprey.iamservice.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @EntityGraph(attributePaths = "roles")
    List<User> findAll();

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<User> findById(Long id);


}
