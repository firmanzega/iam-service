package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.adapter.rest.dto.AuditLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Jejak Audit", description = "Riwayat perubahan penting pada sistem")
@RestController
@RequestMapping("/audit-logs")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Operation(summary = "Daftar jejak audit",
            description = "Diurutkan dari yang terbaru. Hanya untuk peran ADMIN dan AUDITOR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berhasil"),
            @ApiResponse(responseCode = "401", description = "Belum terautentikasi"),
            @ApiResponse(responseCode = "403", description = "Peran tidak cukup")
    })
    @GetMapping
    public Page<AuditLogResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(AuditLogResponse::from);
    }
}
