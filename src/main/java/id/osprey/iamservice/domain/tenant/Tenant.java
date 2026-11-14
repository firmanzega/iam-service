package id.osprey.iamservice.domain.tenant;

import java.time.Instant;

/**
 * Identitas organisasi tenant. tenant_id adalah kunci internal yang dipakai
 * untuk isolasi logis di seluruh tabel inti. organization_id diekspos ke
 * luar lewat header X-Organization-ID.
 *
 * Kelas ini sengaja bebas anotasi JPA: pemetaan ke tabel database adalah
 * urusan lapisan infrastruktur, bukan domain.
 */
public class Tenant {

    private Long id;
    private String name;
    private String organizationId;
    private boolean active;
    private Instant createdAt;

    protected Tenant() {
    }

    public Tenant(Long id, String name, String organizationId, boolean active, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.organizationId = organizationId;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
