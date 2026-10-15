package skillforge.domain.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import skillforge.domain.shared.Rejection;

class ExamGradeTest {

    private final UUID questionId = UUID.randomUUID();

    private final UUID right = UUID.randomUUID();

    private final UUID wrong = UUID.randomUUID();

    @Test
    void roundsTheShareOfRightAnswersToAMarkOutOfTen() {
        Exam exam = exam(Instant.parse("2026-10-20T23:00:00Z"));

        assertEquals(10, ExamGrade.of(exam, Map.of(questionId, right)));
        assertEquals(0, ExamGrade.of(exam, Map.of(questionId, wrong)));
    }

    @Test
    void roundsAPartialExamToTheNearestMark() {
        UUID second = UUID.randomUUID();
        UUID secondRight = UUID.randomUUID();
        UUID secondWrong = UUID.randomUUID();
        Exam exam = new Exam(
                UUID.randomUUID(),
                "Prova",
                null,
                null,
                1,
                0,
                List.of(
                        question(questionId, right, wrong),
                        question(second, secondRight, secondWrong)),
                List.of());

        assertEquals(5, ExamGrade.of(exam, Map.of(questionId, right, second, secondWrong)));
    }

    @Test
    void refusesAnAnswerThatDoesNotMatchTheExam() {
        Exam exam = exam(null);

        assertEquals("invalid-exam", failure(exam, Map.of()));
        assertEquals("invalid-exam", failure(exam, Map.of(UUID.randomUUID(), right)));
        assertEquals("invalid-exam", failure(exam, Map.of(questionId, UUID.randomUUID())));
    }

    @Test
    void aDeadlineThatHasArrivedClosesTheExam() {
        Instant due = Instant.parse("2026-10-20T23:00:00Z");
        Exam exam = exam(due);

        assertFalse(exam.closed(due.minusSeconds(1)));
        assertTrue(exam.closed(due));
        assertFalse(exam(null).closed(due));
    }

    private Exam exam(Instant due) {
        return new Exam(
                UUID.randomUUID(),
                "Prova",
                due,
                null,
                1,
                0,
                List.of(question(questionId, right, wrong)),
                List.of());
    }

    private static Question question(UUID id, UUID correct, UUID incorrect) {
        return new Question(id, "Qual?", List.of(
                new Choice(correct, "Certa", true),
                new Choice(incorrect, "Errada", false)));
    }

    private static String failure(Exam exam, Map<UUID, UUID> answers) {
        return assertThrows(Rejection.class, () -> ExamGrade.of(exam, answers)).failure().wire();
    }

}
