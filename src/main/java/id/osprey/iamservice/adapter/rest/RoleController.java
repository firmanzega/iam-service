package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.role.RoleService;
import id.osprey.iamservice.adapter.rest.dto.CreateRoleRequest;
import id.osprey.iamservice.adapter.rest.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Role", description = "Kelola daftar role yang tersedia")
@RestController
@RequestMapping("/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "Daftar semua role")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berhasil"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi")
    })
    @GetMapping
    public List<RoleResponse> list() {
        return roleService.findAll().stream().map(RoleResponse::from).toList();
    }

    @Operation(summary = "Buat role baru", description = "Hanya untuk peran ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role dibuat"),
            @ApiResponse(responseCode = "400", description = "Nama role tidak valid"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "409", description = "Nama role sudah dipakai")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRoleRequest request) {
        return RoleResponse.from(roleService.create(request.name(), WebMapper.actorId(jwt)));
    }

    @Operation(summary = "Hapus role",
            description = "Role yang masih dipakai pengguna akan ditolak dengan kode 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role dihapus"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "404", description = "Role tidak ditemukan"),
            @ApiResponse(responseCode = "409", description = "Role masih dipakai pengguna")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        roleService.delete(id, WebMapper.actorId(jwt));
    }
}
