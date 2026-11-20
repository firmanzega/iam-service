package id.osprey.iamservice.application.tenant;

import id.osprey.iamservice.domain.user.User;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable result of an idempotent first-ADMIN bootstrap call.
 *
 * A snapshot is used instead of the mutable domain object so callers cannot
 * change the admin state after the use case has returned (SpotBugs
 * EI_EXPOSE_REP).
 *
 * @param id      admin user id
 * @param username login name
 * @param email   email address
 * @param active  whether the account is active
 * @param version optimistic-lock version
 * @param roles   role names, copied defensively
 * @param created true only when this call created the account
 */
public record ProvisionedAdmin(Long id, String username, String email, boolean active,
                               long version, Set<String> roles, boolean created) {

    public ProvisionedAdmin {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * Copies the admin domain object into an immutable snapshot.
     *
     * @param user    the admin after the call
     * @param created whether this call created it
     * @return snapshot safe to pass across the adapter boundary
     */
    public static ProvisionedAdmin from(User user, boolean created) {
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());
        return new ProvisionedAdmin(user.getId(), user.getUsername(), user.getEmail(), user.isActive(),
                user.getVersion(), roleNames, created);
    }
}
