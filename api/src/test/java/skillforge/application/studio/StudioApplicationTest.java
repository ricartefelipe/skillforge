package skillforge.application.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Rejection;

class StudioApplicationTest {

    private final RecordingStudio store = new RecordingStudio();

    private final StudioApplication studio = new StudioApplication(store);

    private final Actor student = new Actor(Role.ALUNO, "aluno");

    @Test
    void searchNeedsTwoCharactersAndStopsAtEighty() {
        assertTrue(studio.search(student, "a").isEmpty());
        assertEquals("peça", studio.search(student, " peça ").get(0).title());
        assertEquals(
                "too-large",
                assertThrows(Rejection.class, () -> studio.search(student, "a".repeat(81))).failure().wire());
    }

    @Test
    void mailNeedsAnAddressWithOneAtSign() {
        studio.saveMail(student, " aluno@example.com ");
        assertEquals("aluno@example.com", store.savedMail);
        assertEquals(
                "mail",
                assertThrows(Rejection.class, () -> studio.saveMail(student, "sem-arroba")).failure().wire());
        assertEquals(
                "mail",
                assertThrows(Rejection.class, () -> studio.saveMail(student, "a b@example.com")).failure().wire());
    }

}
