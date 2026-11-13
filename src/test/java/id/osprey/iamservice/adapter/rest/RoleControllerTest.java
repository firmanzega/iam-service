package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.adapter.rest.dto.CreateRoleRequest;
import id.osprey.iamservice.adapter.rest.dto.RoleResponse;
import id.osprey.iamservice.application.role.RoleService;
import id.osprey.iamservice.domain.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menguji penerjemahan permintaan role tanpa menyalakan Spring.
 *
 * Yang diperiksa adalah kontrak kecil di controller: daftar role dipetakan ke
 * DTO, identitas pelaku diambil dari token, dan penghapusan meneruskan
 * identitas tersebut ke use case.
 */
class RoleControllerTest {

    private static final Long ACTOR_ID = 42L;

    private RoleService roleService;
    private RoleController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        roleService = mock(RoleService.class);
        controller = new RoleController(roleService);
        jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(String.valueOf(ACTOR_ID));
    }

    @Test
    void list_mapsEveryRole() {
        when(roleService.findAll()).thenReturn(List.of(new Role(1L, "ADMIN"), new Role(2L, "USER")));

        List<RoleResponse> responses = controller.list();

        assertEquals(2, responses.size());
        assertEquals("ADMIN", responses.get(0).name());
        assertEquals(2L, responses.get(1).id());
    }

    @Test
    void create_passesActorFromToken() {
        when(roleService.create("SUPERVISOR", ACTOR_ID)).thenReturn(new Role(9L, "SUPERVISOR"));

        RoleResponse response = controller.create(jwt, new CreateRoleRequest("SUPERVISOR"));

        assertEquals(9L, response.id());
        assertEquals("SUPERVISOR", response.name());
    }

    @Test
    void delete_passesActorFromToken() {
        controller.delete(jwt, 7L);

        verify(roleService).delete(7L, ACTOR_ID);
    }
}
