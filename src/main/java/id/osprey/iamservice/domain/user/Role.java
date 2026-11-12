package id.osprey.iamservice.domain.user;

import java.util.Objects;

/**
 * Peran yang bisa dimiliki seorang pengguna.
 *
 * Dibedakan dari entitas database supaya domain tidak bergantung pada JPA.
 * Bandingkan berdasarkan nama, bukan identitas objek, supaya dua salinan Role
 * dengan nama yang sama dianggap sama oleh himpunan.
 */
public class Role {

    private final Long id;
    private final String name;

    protected Role() {
        this(null, null);
    }

    public Role(String name) {
        this(null, name);
    }

    public Role(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Role role)) {
            return false;
        }

        return Objects.equals(name, role.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return "Role{" + name + "}";
    }
}
