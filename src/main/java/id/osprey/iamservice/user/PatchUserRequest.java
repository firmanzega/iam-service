package id.osprey.iamservice.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchUserRequest(
        @Size(max = 50) @Pattern(regexp = "\\S+", message = "must not be blank") String username,
        @Email @Size(min = 1, max = 100) String email,
        @NotNull Long version) {}