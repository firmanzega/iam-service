package id.osprey.iamservice.application.role;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case pengelolaan role.
 */
@Service
public class RoleService {

    private static final String AUDIT_ROLE_CREATED = "ROLE_CREATED";
    private static final String AUDIT_ROLE_DELETED = "ROLE_DELETED";

    private final RoleRepository roleRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Transactional
    public Role create(String name, Long actorUserId) {
        Role saved = roleRepository.save(new Role(name));

        auditService.record(actorUserId, AUDIT_ROLE_CREATED, null, saved.getName());

        return saved;
    }

    /**
     * Menghapus role.
     *
     * Kenapa role yang masih dipakai tidak bisa dihapus:
     *      - Tabel user_roles punya foreign key ke roles. Menghapus role yang
     *        masih terpakai akan membuat data pengguna rusak.
     *      - Karena itu, role ditolak lebih dulu dan pemanggil perlu melepas
     *        role tersebut dari pengguna satu per satu.
     */
    @Transactional
    public void delete(Long roleId, Long actorUserId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new DomainException(DomainException.Reason.ROLE_NOT_FOUND, "Role not found"));

        String name = role.getName();

        try {
            roleRepository.delete(role);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException(DomainException.Reason.ROLE_IN_USE,
                    "Role is still assigned to one or more users");
        }

        auditService.record(actorUserId, AUDIT_ROLE_DELETED, null, name);
    }
}
