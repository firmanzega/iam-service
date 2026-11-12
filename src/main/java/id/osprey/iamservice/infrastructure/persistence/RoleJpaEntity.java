package id.osprey.iamservice.infrastructure.persistence;

import id.osprey.iamservice.domain.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Entitas JPA untuk tabel roles.
 *
 * Pemetaan tabel dan kolom adalah urusan lapisan infrastruktur. Objek domain
 * yang setara ada di id.osprey.iamservice.domain.user.Role.
 */
@Entity
@Table(name = "roles")
public class RoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    protected RoleJpaEntity() {
    }

    public RoleJpaEntity(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Membandingkan berdasarkan nama, sama seperti objek domainnya.
     *
     * Tanpa ini, himpunan role milik pengguna bisadeenpa memeriksa isi,
     * karena Hibernate memakai identitas objek.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof RoleJpaEntity role)) {
            return false;
        }

        return Objects.equals(name, role.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    /**
     * Mengubah entitas database menjadi objek domain.
     */
    public Role toDomain() {
        return new Role(id, name);
    }
}
