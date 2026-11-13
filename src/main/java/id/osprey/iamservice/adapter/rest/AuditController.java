package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.adapter.rest.dto.AuditLogResponse;
import id.osprey.iamservice.adapter.rest.dto.PageResponse;
import id.osprey.iamservice.application.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Jejak Audit", description = "Riwayat perubahan penting pada sistem")
@RestController
@RequestMapping("/v1/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "Daftar jejak audit",
            description = "Properti sort yang diizinkan: id, createdAt, action, actorUserId, "
                    + "targetUserId. Tanpa parameter sort, hasil diurutkan dari yang terbaru "
                    + "dengan id sebagai pemecah seri. Hanya untuk peran ADMIN dan AUDITOR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berhasil"),
            @ApiResponse(responseCode = "400", description = "Properti sort tidak dikenal"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup")
    })
    @GetMapping
    public PageResponse<AuditLogResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(auditService.findAll(WebMapper.toRequest(pageable)), AuditLogResponse::from);
    }
}
