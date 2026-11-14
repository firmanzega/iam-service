package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Menguji adapter tenant: pencarian per organizationId dan jalur simpan.
 *
 * Jalur "simpan tenant yang sudah ada" memuat ulang baris dari database
 * lalu menyalin state; jalur itu mudah tertukar antara menyisipkan baris
 * baru (melanggar UNIQUE organization_id) dan memperbarui yang lama.
 */
@ExtendWith(MockitoExtension.class)
class TenantPersistenceAdapterTest {

    @Mock
    SpringDataTenantRepository repository;

    private TenantPersistenceAdapter adapter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        adapter = new TenantPersistenceAdapter(repository);
    }

    @Test
    void findByOrganizationId_present_mapsToDomain() {
        TenantJpaEntity entity = new TenantJpaEntity("QA Tenant", "org-qa");
        entity.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(repository.findByOrganizationId("org-qa")).thenReturn(Optional.of(entity));

        Optional<Tenant> result = adapter.findByOrganizationId("org-qa");

        assertTrue(result.isPresent());
        assertEquals("QA Tenant", result.get().getName());
        assertEquals("org-qa", result.get().getOrganizationId());
        assertTrue(result.get().isActive());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.get().getCreatedAt());
    }

    @Test
    void findByOrganizationId_absent_isEmpty() {
        when(repository.findByOrganizationId("org-entah")).thenReturn(Optional.empty());

        assertTrue(adapter.findByOrganizationId("org-entah").isEmpty());
    }

    @Test
    void save_newTenant_insertsNewEntity() {
        Tenant tenant = new Tenant(null, "QA Tenant", "org-qa", true, null);

        when(repository.save(any(TenantJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant saved = adapter.save(tenant);

        ArgumentCaptor<TenantJpaEntity> captor = ArgumentCaptor.forClass(TenantJpaEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertEquals("QA Tenant", captor.getValue().getName());
        assertEquals("org-qa", captor.getValue().getOrganizationId());
        assertTrue(captor.getValue().isActive());
        assertEquals("QA Tenant", saved.getName());
    }

    @Test
    void save_existingTenant_updatesTheLoadedRow() {
        TenantJpaEntity existing = new TenantJpaEntity("Nama Lama", "org-qa");
        Tenant tenant = new Tenant(9L, "Nama Baru", "org-qa", false,
                Instant.parse("2026-01-01T00:00:00Z"));

        when(repository.findById(9L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        adapter.save(tenant);

        assertEquals("Nama Baru", existing.getName());
        assertEquals("org-qa", existing.getOrganizationId());
        assertEquals(false, existing.isActive());
        org.mockito.Mockito.verify(repository).save(existing);
    }

    @Test
    void save_existingTenant_missingRow_throws() {
        Tenant tenant = new Tenant(9L, "Nama", "org-qa", true, null);

        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> adapter.save(tenant));
    }
}
