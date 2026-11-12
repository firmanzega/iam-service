package id.osprey.iamservice.application.user;

import id.osprey.iamservice.application.audit.AuditService;
import id.osprey.iamservice.application.security.PasswordPolicy;
import id.osprey.iamservice.domain.auth.TokenRevocationService;
import id.osprey.iamservice.domain.security.PasswordEncoder;
import id.osprey.iamservice.domain.shared.DomainException;
import id.osprey.iamservice.domain.user.Role;
import id.osprey.iamservice.domain.user.RoleRepository;
import id.osprey.iamservice.domain.user.User;
import id.osprey.iamservice.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long ACTOR_ID = 1L;
    private static final Long USER_ID = 1L;

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    PasswordPolicy passwordPolicy;

    @Mock
    AuditService auditService;

    @Mock
    TokenRevocationService tokenRevocationService;

    @InjectMocks
    UserService userService;

    private User user() {
        return new User(USER_ID, "budi", "budi@mail.com", "HASH", true, 0, null,
                null, null, 0L, Set.of());
    }

    @Test
    void search_blankQuery_usesFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        userService.search("   ", pageable);

        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).search(anyString(), any(Pageable.class));
    }

    @Test
    void search_withQuery_trimsAndUsesSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.search("budi", pageable)).thenReturn(new PageImpl<>(List.of()));

        userService.search("  budi  ", pageable);

        verify(userRepository).search("budi", pageable);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void findById_userNotFound_throwsDomainException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> userService.findById(99L));

        assertEquals(DomainException.Reason.USER_NOT_FOUND, ex.getReason());
    }

    @Test
    void create_encodesPasswordBeforeSaving() {
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create("budi", "budi@mail.com", "password123", ACTOR_ID);

        verify(passwordPolicy).validate("password123");
        verify(userRepository).save(any(User.class));
        verify(auditService).record(eq(ACTOR_ID), eq("USER_CREATED"), any(), eq("budi"));
    }

    @Test
    void create_weakPassword_isRejected() {
        org.mockito.Mockito.doThrow(new DomainException(DomainException.Reason.INVALID_PASSWORD, "lemah"))
                .when(passwordPolicy).validate("lemah");

        DomainException ex = assertThrows(DomainException.class,
                () -> userService.create("budi", "budi@mail.com", "lemah", ACTOR_ID));

        assertEquals(DomainException.Reason.INVALID_PASSWORD, ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void update_staleVersion_throwsConflictAndSavesNothing() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        DomainException ex = assertThrows(DomainException.class,
                () -> userService.update(1L, "budi1", "budi1@mail.com", 5L, ACTOR_ID));

        assertEquals(DomainException.Reason.CONCURRENT_MODIFICATION, ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void patch_nullUsername_leavesUsernameUnchanged() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.patch(1L, null, "baru@mail.com", 0L, ACTOR_ID);

        assertEquals("budi", user.getUsername());
        assertEquals("baru@mail.com", user.getEmail());
        verify(userRepository).save(user);
    }

    @Test
    void patch_nullEmail_leavesEmailUnchanged() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.patch(1L, "baru", null, 0L, ACTOR_ID);

        assertEquals("budi@mail.com", user.getEmail());
        assertEquals("baru", user.getUsername());
    }

    @Test
    void deactivate_marksInactiveAndRevokesTokens() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deactivate(1L, ACTOR_ID);

        assertFalse(user.isActive());
        verify(tokenRevocationService).revokeAllTokens(1L);
        verify(auditService).record(eq(ACTOR_ID), eq("USER_DEACTIVATED"), eq(1L), eq("budi"));
    }

    @Test
    void activate_marksActiveAndClearsFailedLogins() {
        User user = new User(USER_ID, "budi", "budi@mail.com", "HASH", false, 3,
                java.time.Instant.now(), null, null, 0L, Set.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.activate(1L, ACTOR_ID);

        assertTrue(user.isActive());
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsUnauthorized() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("salah", "HASH")).thenReturn(false);

        DomainException ex = assertThrows(DomainException.class,
                () -> userService.changePassword(1L, "salah", "BaruBanget1", ACTOR_ID));

        assertEquals(DomainException.Reason.CURRENT_PASSWORD_MISMATCH, ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_sameAsCurrent_isRejected() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("sama", "HASH")).thenReturn(true);

        DomainException ex = assertThrows(DomainException.class,
                () -> userService.changePassword(1L, "sama", "sama", ACTOR_ID));

        assertEquals(DomainException.Reason.INVALID_PASSWORD, ex.getReason());
    }

    @Test
    void changePassword_valid_revokesTokensAndAudits() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("lama", "HASH")).thenReturn(true);
        when(passwordEncoder.matches("baru", "HASH")).thenReturn(false);
        when(passwordEncoder.encode("baru")).thenReturn("HASH_BARU");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword(1L, "lama", "baru", ACTOR_ID);

        assertEquals("HASH_BARU", user.getPasswordHash());
        verify(tokenRevocationService).revokeAllTokens(1L);
        verify(auditService).record(eq(ACTOR_ID), eq("PASSWORD_CHANGED"), eq(1L), eq("budi"));
    }

    @Test
    void resetPassword_setsNewPasswordWithoutCurrentOne() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("baru")).thenReturn("HASH_BARU");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.resetPassword(1L, "baru", ACTOR_ID);

        assertEquals("HASH_BARU", user.getPasswordHash());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(tokenRevocationService).revokeAllTokens(1L);
    }

    @Test
    void replaceRoles_unknownRole_throwsBadRequest() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByNameIn(Set.of("USER", "NGAWUR"))).thenReturn(Set.of());

        DomainException ex = assertThrows(DomainException.class,
                () -> userService.replaceRoles(1L, Set.of("USER", "NGAWUR"), 0L, ACTOR_ID));

        assertEquals(DomainException.Reason.UNKNOWN_ROLE, ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void replaceRoles_success_replacesRolesAndRevokesTokens() {
        User user = user();
        Role role = new Role(7L, "USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByNameIn(Set.of("USER"))).thenReturn(Set.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.replaceRoles(1L, Set.of("USER"), 0L, ACTOR_ID);

        assertEquals(1, user.getRoles().size());
        verify(tokenRevocationService).revokeAllTokens(1L);
        verify(auditService).record(eq(ACTOR_ID), eq("ROLES_REPLACED"), eq(1L), eq("USER"));
    }
}
