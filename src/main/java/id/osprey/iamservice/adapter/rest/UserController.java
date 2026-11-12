package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.user.UserService;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserSortableProperties;
import id.osprey.iamservice.adapter.rest.dto.ChangePasswordRequest;
import id.osprey.iamservice.adapter.rest.dto.CreateUserRequest;
import id.osprey.iamservice.adapter.rest.dto.PatchUserRequest;
import id.osprey.iamservice.adapter.rest.dto.ReplaceRolesRequest;
import id.osprey.iamservice.adapter.rest.dto.ResetPasswordRequest;
import id.osprey.iamservice.adapter.rest.dto.UpdateUserRequest;
import id.osprey.iamservice.adapter.rest.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Pengguna", description = "Kelola data pengguna dan role-nya")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Daftar pengguna",
            description = """
                    Mendukung pencarian pada username dan email, serta pengurutan.

                    Ukuran halaman dibatasi maksimum 50 baris. Properti pengurutan
                    yang tidak diizinkan akan ditolak dengan kode 400, supaya
                    kolom sensitif seperti hash kata sandi tidak bisa dipakai
                    sebagai kunci pengurutan.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berhasil"),
            @ApiResponse(responseCode = "400", description = "Properti pengurutan tidak diizinkan"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi")
    })
    @GetMapping
    public Page<UserResponse> list(@RequestParam(required = false) String q,
                                   @PageableDefault(size = 10, sort = ("id")) Pageable pageable) {

        validateSort(pageable);

        return userService.search(q, pageable).map(UserResponse::from);
    }

    @Operation(summary = "Ambil satu pengguna berdasarkan id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berhasil"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan")
    })
    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return UserResponse.from(userService.findById(id));
    }

    @Operation(summary = "Buat pengguna baru", description = "Hanya untuk peran ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pengguna dibuat"),
            @ApiResponse(responseCode = "400", description = "Isi permintaan tidak valid"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "409", description = "Username atau email sudah dipakai")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserRequest request) {
        User created = userService.create(request.username(), request.email(), request.password(), actorId(jwt));
        return UserResponse.from(created);
    }

    @Operation(summary = "Ganti seluruh data pengguna",
            description = "Hanya untuk peran ADMIN. Memakai penguncian optimistis lewat field version.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berhasil"),
            @ApiResponse(responseCode = "400", description = "Isi permintaan tidak valid"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan"),
            @ApiResponse(responseCode = "409", description = "Versi data sudah berubah oleh pihak lain")
    })
    @PutMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable Long id,
                                @Valid @RequestBody UpdateUserRequest request) {
        User updated = userService.update(id, request.username(), request.email(), request.version(), actorId(jwt));
        return UserResponse.from(updated);
    }

    @Operation(summary = "Ubah sebagian data pengguna",
            description = "Hanya untuk peran ADMIN. Field yang tidak ikut dikirim tidak berubah.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berhasil"),
            @ApiResponse(responseCode = "400", description = "Isi permintaan tidak valid"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan"),
            @ApiResponse(responseCode = "409", description = "Versi data sudah berubah oleh pihak lain")
    })
    @PatchMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable Long id,
                                @Valid @RequestBody PatchUserRequest request) {
        User updated = userService.patch(id, request.username(), request.email(), request.version(), actorId(jwt));
        return UserResponse.from(updated);
    }

    @Operation(summary = "Nonaktifkan pengguna",
            description = """
                    Setelah dinonaktifkan, seluruh token refresh milik pengguna
                    dicabut sehingga sesi yang sedang berjalan ikut berakhir.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pengguna dinonaktifkan"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan")
    })
    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        userService.deactivate(id, actorId(jwt));
    }

    @Operation(summary = "Aktifkan kembali pengguna",
            description = "Riwayat kegagalan login juga ikut dihapus.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pengguna diaktifkan"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan")
    })
    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        userService.activate(id, actorId(jwt));
    }

    @Operation(summary = "Ganti seluruh role pengguna",
            description = """
                    Role yang tidak dikirim akan dilepas. Role yang tidak dikenal
                    akan ditolak dengan kode 400.

                    Setelah role berubah, seluruh token refresh pengguna dicabut,
                    supaya role pada token akses berikutnya benar-benar yang
                    terbaru.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role diganti"),
            @ApiResponse(responseCode = "400", description = "Ada role yang tidak dikenal"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan"),
            @ApiResponse(responseCode = "409", description = "Versi data sudah berubah oleh pihak lain")
    })
    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable Long id,
                                    @Valid @RequestBody ReplaceRolesRequest request) {
        User updated = userService.replaceRoles(id, request.roleNames(), request.version(), actorId(jwt));
        return UserResponse.from(updated);
    }

    /**
     * Mengganti kata sandi sendiri.
     *
     * Pemeriksaan di sini memakai annotation, bukan if di dalam method:
     *      - Aturan akses berada satu tingkat di tempat yang mudah dicari, yaitu
     *        tepat di atas method yang dilindungi.
     *      - Kalau aturan ditulis manual di dalam method, ada kemungkinan
     *        endpoint baru dibuat tanpa aturan sama sekali.
     *
     * Bandingkan nama pada token dengan id pada path. Nama pada token adalah
     * claim "sub" yang diisi dengan id pengguna.
     */
    @Operation(summary = "Ganti kata sandi sendiri",
            description = """
                    Setelah kata sandi diganti, seluruh token refresh dicabut.
                    Alasan: bila kata sandi diganti karena ada kecurigaan kata
                    sandi bocor, sesi lama harus ikut mati.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Kata sandi diganti"),
            @ApiResponse(responseCode = "400", description = "Kata sandi baru tidak memenuhi aturan"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi atau kata sandi lama salah"),
            @ApiResponse(responseCode = "403", description = "Hanya pemilik akun yang boleh mengganti kata sandinya")
    })
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable Long id,
                                @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request.currentPassword(), request.newPassword(), actorId(jwt));
    }

    /**
     * Administrator menetapkan kata sandi baru tanpa perlu mengetahui kata
     * sandi lama pengguna.
     */
    @Operation(summary = "Tetapkan kata sandi baru oleh administrator",
            description = "Seluruh token refresh pengguna langsung dicabut dan riwayat gagal login dihapus.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Kata sandi ditetapkan"),
            @ApiResponse(responseCode = "400", description = "Kata sandi baru tidak memenuhi aturan"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup"),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan")
    })
    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable Long id,
                               @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword(), actorId(jwt));
    }

    /**
     * Identitas pelaku perubahan, diambil dari claim "sub" pada token.
     */
    private Long actorId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }

    /**
     * Menolak pengurutan berdasarkan properti di luar daftar yang diizinkan.
     */
    private void validateSort(Pageable pageable) {
        List<String> rejected = pageable.getSort().stream()
                .map(order -> order.getProperty())
                .filter(property -> !UserSortableProperties.ALLOWED.contains(property))
                .distinct()
                .toList();

        if (!rejected.isEmpty()) {
            throw new DomainException(DomainException.Reason.INVALID_SORT_PROPERTY,
                    "Properties not allowed for sorting: " + rejected
                            + ". Allowed: " + UserSortableProperties.ALLOWED);
        }
    }
}
