package id.osprey.iamservice.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port untuk akses data role.
 */
public interface RoleRepository {

    Optional<Role> findById(Long id);

    Optional<Role> findByName(String name);

    /**
     * Mencari sekumpulan role berdasarkan nama.
     *
     * @param names nama-nama role yang dicari
     * @return role yang ditemukan, kemungkinan lebih sedikit dari yang diminta
     */
    Set<Role> findByNameIn(Set<String> names);

    Role save(Role role);

    void delete(Role role);

    /**
     * Mengambil seluruh role.
     *
     * Jumlah role kecil dan selalu Dimuat sekaligus, jadi tidak perlu paginasi.
     */
    List<Role> findAll();
}
