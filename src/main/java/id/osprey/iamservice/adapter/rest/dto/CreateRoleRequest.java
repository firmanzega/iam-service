package id.osprey.iamservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param name nama role, contoh SUPERVISOR
 */
public record CreateRoleRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                message = "must be uppercase letters, digits or underscore and start with a letter")
        String name) {
}
