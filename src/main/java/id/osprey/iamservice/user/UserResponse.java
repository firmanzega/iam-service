package id.osprey.iamservice.user;

import java.util.Set;

public record UserResponse(Long id, String username, String email, boolean active, Long version, Set<String> roles) {}
