package skillforge.application.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import skillforge.application.notice.MemoryNotices;
import skillforge.domain.cohort.CriterionDraft;
import skillforge.domain.cohort.CriterionMark;
import skillforge.domain.cohort.PracticeRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Rejection;

class PracticeApplicationTest {

    private final MemoryNotices store = new MemoryNotices();

    private final MemoryPractices memory = new MemoryPractices();

    private final PracticeApplication practices = new PracticeApplication(memory, store);

    private final Actor teacher = new Actor(Role.DOCENTE, "docente");

    private final UUID cohort = UUID.randomUUID();

    @Test
    void aRubricCannotExceedTenOrSkipAName() {
        assertEquals("forbidden", failure(new Actor(Role.ALUNO, "aluno"), "Peça", "Faça.", List.of()));
        assertEquals("blank-practice", failure(teacher, " ", "Faça.", List.of()));
        assertEquals("rubric", failure(teacher, "Peça", "Faça.", List.of(
                new CriterionDraft("Clareza", 6),
                new CriterionDraft("Exemplo", 6))));
        assertEquals("rubric", failure(teacher, "Peça", "Faça.", List.of(new CriterionDraft("Clareza", 0))));
    }

    @Test
    void feedbackUsesTheMarkOrTheSumOfTheRubricAndTellsTheStudent() {
        UUID practice = UUID.randomUUID();
        practices.feedback(teacher, cohort, practice, " aluno ", " Bom. ", 8, null);
        assertEquals(8, memory.grade);
        practices.feedback(teacher, cohort, practice, "aluno", "Bom.", null, List.of(
                new CriterionMark(UUID.randomUUID(), 4),
                new CriterionMark(UUID.randomUUID(), 5)));
        assertEquals(9, memory.grade);
        assertEquals("A devolutiva da prática foi publicada.", store.notice);
    }

    private String failure(Actor actor, String title, String prompt, List<CriterionDraft> criteria) {
        return assertThrows(Rejection.class, () -> practices.open(actor, cohort, title, prompt, null, criteria))
                .failure()
                .wire();
    }

    private final class MemoryPractices implements PracticeRepository {

        private int grade;

        @Override
        public UUID openPractice(
                UUID cohortId,
                Actor actor,
                String title,
                String prompt,
                java.time.Instant dueAt,
                List<CriterionDraft> criteria) {
            return UUID.randomUUID();
        }

        @Override
        public void submit(UUID cohortId, UUID practiceId, Actor actor, String text) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removePractice(UUID cohortId, Actor actor, UUID practiceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<skillforge.domain.cohort.Practice> inCohort(UUID cohortId, Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void feedback(
                UUID cohortId,
                UUID practiceId,
                Actor actor,
                String student,
                String text,
                int given,
                List<CriterionMark> marks) {
            assertEquals("aluno", student);
            grade = given;
        }

        @Override
        public void revise(
                UUID cohortId,
                Actor actor,
                UUID practiceId,
                String title,
                String prompt,
                java.time.Instant dueAt) {
            throw new UnsupportedOperationException();
        }

    }

}
