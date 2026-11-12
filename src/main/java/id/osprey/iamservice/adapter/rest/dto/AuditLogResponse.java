package id.osprey.iamservice.adapter.rest.dto;

import id.osprey.iamservice.domain.audit.AuditLog;

import java.time.Instant;

/**
 * @param id identitas catatan
 * @param actorUserId pelaku perubahan, null untuk aksi otomatis
 * @param action nama aksi, contoh USER_UPDATED
 * @param targetUserId objek yang diubah, null bila tidak ada
 * @param details keterangan tambahan dalam bentuk teks
 * @param createdAt waktu pencatatan
 */
public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String action,
        Long targetUserId,
        String details,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getAction(),
                auditLog.getTargetUserId(),
                auditLog.getDetails(),
                auditLog.getCreatedAt());
    }
}
