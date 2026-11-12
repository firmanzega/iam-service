package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.adapter.rest.dto.AuditLogResponse;
import id.osprey.iamservice.adapter.rest.dto.PageResponse;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.domain.shared.PaginationRequest;
import id.osprey.iamservice.domain.shared.SortSpec;
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
    public PageResponse<AuditLogResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        PaginationRequest request = new PaginationRequest(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().stream()
                        .map(order -> new SortSpec(order.getProperty(), order.isDescending()))
                        .toList());

        return PageResponse.from(auditLogRepository.findAll(request), AuditLogResponse::from);
    }
}
