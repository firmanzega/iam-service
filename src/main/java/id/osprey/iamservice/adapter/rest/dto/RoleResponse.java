package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.domain.user.Role;

/**
 * @param id identitas role
 * @param name nama role, contoh ADMIN
 */
public record RoleResponse(Long id, String name) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
