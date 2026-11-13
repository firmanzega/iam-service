package id.osprey.iamservice.domain.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji kesamaan role berdasarkan nama.
 *
 * Kenapa ini penting: himpunan role memakai equals dan hashCode. Kalau
 * perbandingannya berubah menjadi identitas objek, role yang sama bisa masuk
 * dua kali dari dua sumber berbeda tanpa terlihat.
 */
class RoleTest {

    @Test
    void sameName_isEqualEvenWhenIdDiffers() {
        Role fromDatabase = new Role(1L, "ADMIN");
        Role fromRequest = new Role("ADMIN");

        assertEquals(fromDatabase, fromRequest);
        assertEquals(fromDatabase.hashCode(), fromRequest.hashCode());
    }

    @Test
    void differentName_isNotEqual() {
        assertNotEquals(new Role("ADMIN"), new Role("USER"));
    }

    @Test
    void sameInstance_isEqual() {
        Role role = new Role(1L, "ADMIN");

        assertEquals(role, role);
    }

    @Test
    void otherType_isNotEqual() {
        assertFalse(new Role("ADMIN").equals("ADMIN"));
    }

    @Test
    void nullName_isEqualOnlyWithOtherNullName() {
        assertEquals(new Role(1L, null), new Role(2L, null));
        assertNotEquals(new Role(1L, null), new Role(1L, "ADMIN"));
    }

    @Test
    void accessors_returnStoredValues() {
        Role role = new Role(7L, "AUDITOR");

        assertEquals(7L, role.getId());
        assertEquals("AUDITOR", role.getName());
    }

    @Test
    void toString_showsRoleName() {
        assertTrue(new Role("ADMIN").toString().contains("ADMIN"));
    }
}
