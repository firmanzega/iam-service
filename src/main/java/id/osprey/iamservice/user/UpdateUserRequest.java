package id.osprey.iamservice.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest (
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 100) @Email String email,
        @NotNull Long version){}
