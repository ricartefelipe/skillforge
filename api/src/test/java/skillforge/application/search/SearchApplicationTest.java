package skillforge.application.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.search.SearchRepository;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.Hit;

class SearchApplicationTest {

    private final SearchApplication search = new SearchApplication(new MemorySearch());

    private final Actor student = new Actor(Role.ALUNO, "aluno");

    @Test
    void searchNeedsTwoCharactersAndStopsAtEighty() {
        assertTrue(search.search(student, "a").isEmpty());
        assertEquals("peça", search.search(student, " peça ").get(0).title());
        assertEquals(
                "too-large",
                assertThrows(Rejection.class, () -> search.search(student, "a".repeat(81))).failure().wire());
    }

    private static final class MemorySearch implements SearchRepository {

        @Override
        public List<Hit> search(Actor actor, String query) {
            return List.of(new Hit("aula", query, "/aulas"));
        }

    }

}
