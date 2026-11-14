package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.tenant.Tenant;
import id.osprey.iamservice.domain.tenant.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter yang memenuhi port TenantRepository memakai Spring Data JPA.
 */
@Repository
public class TenantPersistenceAdapter implements TenantRepository {

    private final SpringDataTenantRepository repository;

    public TenantPersistenceAdapter(SpringDataTenantRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Tenant> findByOrganizationId(String organizationId) {
        return repository.findByOrganizationId(organizationId).map(TenantJpaEntity::toDomain);
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantJpaEntity entity = tenant.getId() == null
                ? new TenantJpaEntity(tenant.getName(), tenant.getOrganizationId())
                : repository.findById(tenant.getId()).orElseThrow();
        entity.setName(tenant.getName());
        entity.setOrganizationId(tenant.getOrganizationId());
        entity.setActive(tenant.isActive());
        return repository.save(entity).toDomain();
    }
}
