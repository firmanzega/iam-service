package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.application.auth.AuthService;
import id.osprey.iamservice.adapter.rest.dto.LoginRequest;
import id.osprey.iamservice.adapter.rest.dto.RefreshRequest;
import id.osprey.iamservice.adapter.rest.dto.SessionResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Autentikasi", description = "Login, penukaran token refresh, dan logout")
@RestController
@RequestMapping("/v1/auth")
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

                    Field deviceLabel opsional. Nilainya hanya membantu pemilik akun
                    membedakan perangkatnya sendiri, bukan dipakai sebagai identitas.
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
        return TokenResponse.from(
                authService.login(request.identifier(), request.password(), request.deviceLabel()));
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
        authService.logout(WebMapper.actorId(jwt), request.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Daftar sesi perangkat pemanggil",
            description = """
                    Hanya sesi aktif yang dikembalikan, terbaru lebih dulu.

                    Sesi yang ditandai current adalah sesi pada token yang dipakai
                    memanggil endpoint ini. Sesi yang sudah dicabut atau tidak dipakai
                    lebih lama dari masa berlaku token refresh tidak ditampilkan.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daftar sesi"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi")
    })
    @GetMapping("/sessions")
    public List<SessionResponse> listSessions(@AuthenticationPrincipal Jwt jwt) {
        return authService.listSessions(WebMapper.actorId(jwt), WebMapper.sessionId(jwt)).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @Operation(summary = "Cabut satu sesi perangkat",
            description = """
                    Sesi yang dicabut beserta seluruh token refresh di dalamnya langsung
                    tidak bisa dipakai. Perangkat lain milik pengguna yang sama tidak
                    terpengaruh.

                    Sesi milik pengguna lain dan sesi yang tidak ada sama-sama dijawab
                    404. Perbedaan jawaban akan membocorkan sesi mana yang benar-benar
                    ada.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesi dicabut"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "404", description = "Sesi tidak ditemukan atau bukan milik pemanggil")
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID sessionId) {
        authService.revokeSession(WebMapper.actorId(jwt), sessionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
