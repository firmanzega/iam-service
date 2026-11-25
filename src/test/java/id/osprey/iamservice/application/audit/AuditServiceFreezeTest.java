package id.osprey.iamservice.application.audit;

import id.osprey.iamservice.application.maintenance.MaintenanceState;
import id.osprey.iamservice.domain.audit.AuditLogRepository;
import id.osprey.iamservice.domain.tenant.TenantContext;
import id.osprey.iamservice.domain.tenant.TenantRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuditServiceFreezeTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void clearTenantAndRegistry() {
        TenantContext.clear();
        meterRegistry.close();
    }

    @Test
    void recordDuringFreezeThrowsAndDoesNotSave() {
        MaintenanceState state = new MaintenanceState(true);
        AuditService service = new AuditService(auditLogRepository, tenantRepository, state, meterRegistry);

        assertThrows(AuditSuppressedException.class,
                () -> service.record(1L, "USER_UPDATED", 2L, "detail"));

        verify(auditLogRepository, never()).save(any());
        assertEquals(1, suppressedCount());
    }

    @Test
    void recordIndependentlyDuringFreezeThrowsAndDoesNotSave() {
        MaintenanceState state = new MaintenanceState(true);
        AuditService service = new AuditService(auditLogRepository, tenantRepository, state, meterRegistry);

        assertThrows(AuditSuppressedException.class,
                () -> service.recordIndependently(1L, "ACCESS_DENIED", null, "detail"));

        verify(auditLogRepository, never()).save(any());
        assertEquals(1, suppressedCount());
    }

    @Test
    void recordOutsideFreezeStillSaves() {
        MaintenanceState state = new MaintenanceState(false);
        AuditService service = new AuditService(auditLogRepository, tenantRepository, state, meterRegistry);

        service.record(1L, "USER_UPDATED", 2L, "detail");

        verify(auditLogRepository).save(any());
        assertEquals(0, suppressedCount());
    }

    private double suppressedCount() {
        var counter = meterRegistry.find("iam.audit.suppressed").counter();
        return counter == null ? 0 : counter.count();
    }
}
