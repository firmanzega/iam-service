package id.osprey.iamservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository Spring Data untuk entitas JPA tenant.
 */
public interface SpringDataTenantRepository extends JpaRepository<TenantJpaEntity, Long> {

    Optional<TenantJpaEntity> findByOrganizationId(String organizationId);
}
