package id.osprey.iamservice.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ReplaceRolesRequest(
        @NotNull Set<@NotBlank String> roleNames,
        @NotNull Long version) {}
