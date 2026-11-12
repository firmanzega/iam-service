package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.auth.AuthService;
import id.osprey.iamservice.adapter.rest.dto.LoginRequest;
import id.osprey.iamservice.adapter.rest.dto.RefreshRequest;
import id.osprey.iamservice.adapter.rest.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autentikasi", description = "Login, penukaran token refresh, dan logout")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Login dan terima sepasang token",
            description = """
                    Identifier boleh berupa username maupun email.

                    Jika kata sandi salah maupun identifier tidak dikenal, jawabannya
                    selalu sama persis. Ini supaya pemanggil tidak bisa memakai
                    perbedaan pesan untuk menebak username mana yang terdaftar.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login berhasil"),
            @ApiResponse(responseCode = "400", description = "Isi permintaan tidak valid"),
            @ApiResponse(responseCode = "401", description = "Kredensial salah"),
            @ApiResponse(responseCode = "423", description = "Akun terkunci karena terlalu banyak kegagalan")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.identifier(), request.password()));
    }

    @Operation(summary = "Tukar token refresh menjadi token baru",
            description = """
                    Token refresh lama langsung dicabut setelah ditukar. Kalau token
                    lama dipakai lagi, jawabannya 401.

                    Token akses baru berisi role terbaru, jadi perubahan role langsung
                    berlaku tanpa perlu menunggu token akses lama kedaluwarsa.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Penukaran berhasil"),
            @ApiResponse(responseCode = "400", description = "Isi permintaan tidak valid"),
            @ApiResponse(responseCode = "401", description = "Token refresh tidak dikenal, dicabut, atau kedaluwarsa"),
            @ApiResponse(responseCode = "423", description = "Akun terkunci")
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Cabut token refresh milik pemanggil",
            description = """
                    Token akses yang sudah terbit tetap berlaku sampai kedaluwarsa,
                    karena token akses tidak disimpan di server. Untuk mencabutnya
                    seketika, seluruh token refresh pengguna dicabut dan pengguna
                    diminta login ulang.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Token refresh dicabut"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Token refresh bukan milik pemanggil")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody RefreshRequest request) {
        authService.logout(Long.valueOf(jwt.getSubject()), request.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
