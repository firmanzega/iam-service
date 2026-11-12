package id.osprey.iamservice.application.role;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final Long ACTOR_ID = 1L;

    @Mock
    RoleRepository roleRepository;

    @Mock
    AuditService auditService;

    @InjectMocks
    RoleService roleService;

    @Test
    void create_newRole_isSavedAndAudited() {
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role created = roleService.create("SUPERVISOR", ACTOR_ID);

        assertEquals("SUPERVISOR", created.getName());
        verify(roleRepository).save(any(Role.class));
        verify(auditService).record(eq(ACTOR_ID), eq("ROLE_CREATED"), eq(null), eq("SUPERVISOR"));
    }

    @Test
    void delete_existingRole_removesItAndAudits() {
        Role role = new Role(1L, "SUPERVISOR");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        roleService.delete(1L, ACTOR_ID);

        verify(roleRepository).delete(role);
        verify(auditService).record(eq(ACTOR_ID), eq("ROLE_DELETED"), eq(null), eq("SUPERVISOR"));
    }

    @Test
    void delete_unknownRole_throwsNotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> roleService.delete(99L, ACTOR_ID));

        assertEquals(DomainException.Reason.ROLE_NOT_FOUND, ex.getReason());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void delete_roleStillInUse_throwsConflict() {
        Role role = new Role(1L, "SUPERVISOR");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("masih dipakai"))
                .when(roleRepository).delete(any());

        DomainException ex = assertThrows(DomainException.class, () -> roleService.delete(1L, ACTOR_ID));

        assertEquals(DomainException.Reason.DUPLICATE_RESOURCE, ex.getReason());
    }

    @Test
    void findAll_returnsAllRoles() {
        when(roleRepository.findAll()).thenReturn(List.of(new Role(1L, "ADMIN"), new Role(2L, "USER")));

        assertEquals(2, roleService.findAll().size());
    }
}
