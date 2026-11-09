package id.osprey.iamservice.user;

import id.osprey.iamservice.role.ReplaceRolesRequest;
import id.osprey.iamservice.role.Role;
import id.osprey.iamservice.role.RoleRepository;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    /**
     * Dua perintah Mockito yang sering muncul:
     *      - when(X).thenReturn(Y) -> “kalau X dipanggil, jawab Y.” Ini naskah untuk aktor.
     *      - verify(X): “pastikan X benar-benar dipanggil.” Dipakai untuk memeriksa apa yang dilakukan service, bukan hanya hasilnya.
     */

    @Test
    void findById_userNotFound_throws404() {
        // Naskah: “kalau ada yang mencari user id 99, jawab: tidak ada.”
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // assertThrows artinya: “jalankan kode ini, dan harus melempar exception tipe ini.”
        // Kalau tidak melempar, tes gagal. Exception yang dilempar ditangkap ke ex.
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class, () -> userService.findById(99L));

        // assertEquals(diharapkan, sebenarnya): pastikan statusnya memang 404.
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void patch_staleVersion_throwsOptimisticLockAndSavesNothing() {

        // Kita buat user tiruan yang mengaku version = 5,
        // lalu repository mengembalikannya saat dicari id 1.
        User user = mock(User.class);
        when(user.getVersion()).thenReturn(5L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Klien mengirim version = 3. Beda dengan 5, jadi basi.
        PatchUserRequest request = new PatchUserRequest(null, "new@mail.com", 3L);

        // Harus melempar exception konflik.
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> userService.patch(1L, request));

        // “Pastikan saveAndFlush tidak pernah dipanggil dengan data apa pun (any()).”
        // Ini membuktikan bahwa versi basi tidak menyimpan apa-apa.
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_encodesPasswordBeforeSaving() {

        // Encoder tiruan: “kalau diminta meng-encode password123, jawab HASHED.”
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");

        // Repository tiruan: “kalau diminta menyimpan sebuah User, kembalikan User yang sama persis”
        // (inv.getArgument(0) = argumen pertama yang diterima).
        // Tanpa ini, mock mengembalikan null dan toResponse meledak, itu NPE  (NullPointerException) yang kamu temui tadi.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(new CreateUserRequest("budi", "budi@mail.com", "password123"));

        // ArgumentCaptor itu “penyadap”: menangkap objek User yang dikirim ke repository, supaya kita bisa membukanya.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);


        verify(userRepository).save(captor.capture());
        assertEquals("HASHED", captor.getValue().getPasswordHash());
    }

    @Test
    void deactivate_setsActiveFalse() {
        User user = new User("budi", "budi@mail.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivate(1L);

        assertFalse(user.isActive());
    }

    @Test
    void activate_setsActiveTrue() {
        User user = new User("andi", "andi@mail.com", "passwordHash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.activate(1L);

        assertTrue(user.isActive());

    }

    @Test
    void deactivate_userNotFound_throws404() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.deactivate(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void activate_userNotFound_throws404() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.activate(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void patch_changeEmail_withUsernameIsNull() {

        // Catatan penting soal spy: pakai doReturn(...).when(user).getVersion(), bukan when(user.getVersion()).thenReturn(...).
        // Pada spy, bentuk kedua memanggil method aslinya dulu saat menyusun stub.
        User user = spy( new User("budi", "budi@mail.com", "hashPassword"));
        doReturn(0L).when(user).getVersion();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);


        PatchUserRequest request = new PatchUserRequest(null, "new_budi@mail.com",0L);
        userService.patch(1L, request);

        verify(user).changeEmail(request.email());
        verify(user, never()).changeUsername(any());

    }

    @Test
    void update_throwsOptimisticLockAndSavesNothing() {

        User user = spy( new User("budi", "budi@mail.com", "hashPassword"));
        doReturn(5L).when(user).getVersion();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest("budi1", "budi1@mail.com", 1L);

        assertThrows(ObjectOptimisticLockingFailureException.class, () ->  userService.update(1L, request));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void findAll_pageable_q_NullOrSpace(String q) {

        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        userService.search(q, pageable);

        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).search(any(), any());

    }

    @Test
    void findAll_pageable_q_Trim() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.search("budi", pageable)).thenReturn(new PageImpl<>(List.of()));

        userService.search("  budi  ", pageable);

        verify(userRepository).search("budi", pageable);
        verify(userRepository, never()).findAll(any(Pageable.class));

    }

    @Test
    void replaceRoles_throwsUnknownRole_400() {

        User user = mock(User.class);
        when(user.getVersion()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Role role = mock(Role.class);
        when(role.getName()).thenReturn("USER");

        when(roleRepository.findByNameIn(anyCollection())).thenReturn(Set.of(role));

        ReplaceRolesRequest request = new ReplaceRolesRequest(Set.of("USER", "NGAWUR"), 1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.replaceRoles(1L, request));

        assertTrue(ex.getReason().contains("NGAWUR"));
        verify(userRepository, never()).saveAndFlush(any());
        verify(user, never()).replaceRoles(any());

    }

    @Test
    void replaceRoles_success() {

        User user = mock(User.class);
        when(user.getVersion()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Set<Role> roles = Set.of(mock(Role.class), mock(Role.class));
        when(roleRepository.findByNameIn(anyCollection())).thenReturn(roles);

        when(userRepository.saveAndFlush(user)).thenReturn(user);

        ReplaceRolesRequest request = new ReplaceRolesRequest(Set.of("USER","ADMIN"), 1L);

        userService.replaceRoles(1L, request);

        verify(user).replaceRoles(roles);

    }

}
