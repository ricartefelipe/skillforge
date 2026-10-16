package skillforge.application.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import skillforge.application.notice.MemoryNotices;
import skillforge.domain.bank.BankRepository;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.cohort.ExamRepository;
import skillforge.domain.cohort.LessonProgress;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Rejection;

class ExamApplicationTest {

    private final MemoryNotices store = new MemoryNotices();

    private final UUID opened = UUID.randomUUID();

    private final FinishedLessons lessons = new FinishedLessons();

    private final ExamApplication exams = new ExamApplication(new MemoryExams(), new EmptyBank(), store, lessons);

    private final Actor teacher = new Actor(Role.DOCENTE, "docente");

    private final UUID cohort = UUID.randomUUID();

    @Test
    void aStudentCannotPublishAnExam() {
        assertEquals("forbidden", failure(new Actor(Role.ALUNO, "aluno"), "Prova", 1, null, question()));
    }

    @Test
    void refusesAnExamWithoutOneRightChoiceOrALegalClock() {
        assertEquals("blank-exam", failure(teacher, "  ", 1, null, question()));
        assertEquals("invalid-exam", failure(teacher, "Prova", 1, null));
        assertEquals("invalid-exam", failure(teacher, "Prova", 0, null, question()));
        assertEquals("invalid-exam", failure(teacher, "Prova", 6, null, question()));
        assertEquals("invalid-exam", failure(teacher, "Prova", 1, 4, question()));
        assertEquals("invalid-exam", failure(teacher, "Prova", 1, 181, question()));
        ExamDraft.QuestionDraft twoRights = new ExamDraft.QuestionDraft("Qual?", List.of(
                new ExamDraft.ChoiceDraft("A", true),
                new ExamDraft.ChoiceDraft("B", true)));
        assertEquals("invalid-exam", failure(teacher, "Prova", 1, null, twoRights));
    }

    @Test
    void publishesTheExamAndTellsTheCohort() {
        Opened created = exams.open(teacher, cohort, " Fundamentos ", null, 20, null, null, List.of(question()));

        assertEquals(opened, created.id());
        assertEquals("Fundamentos", created.name());
        assertEquals(cohort, store.cohortNotice);
        assertEquals("Nova prova: Fundamentos", store.notice);
    }

    @Test
    void aStudentFinishesTheLessonsBeforeTheExam() {
        lessons.ready = false;
        assertEquals(
                "lessons",
                assertThrows(Rejection.class, () -> exams.take(
                        new Actor(Role.ALUNO, "aluno"),
                        cohort,
                        opened,
                        Map.of()))
                        .failure()
                        .wire());
        lessons.ready = true;
    }

    @Test
    void aStudentMustStartBeforeAnswering() {
        exams.start(new Actor(Role.ALUNO, "aluno"), cohort, opened);
        assertEquals(
                "forbidden",
                assertThrows(Rejection.class, () -> exams.start(teacher, cohort, opened)).failure().wire());
        exams.take(new Actor(Role.ALUNO, "aluno"), cohort, opened, Map.of());
        assertEquals(
                "invalid-exam",
                assertThrows(Rejection.class, () -> exams.take(new Actor(Role.ALUNO, "aluno"), cohort, opened, null))
                        .failure()
                        .wire());
    }

    private String failure(
            Actor actor,
            String title,
            Integer attempts,
            Integer minutes,
            ExamDraft.QuestionDraft... questions) {
        return assertThrows(
                Rejection.class,
                () -> exams.open(actor, cohort, title, null, minutes, attempts, null, List.of(questions)))
                .failure()
                .wire();
    }

    private static ExamDraft.QuestionDraft question() {
        return new ExamDraft.QuestionDraft(" Qual? ", List.of(
                new ExamDraft.ChoiceDraft(" Certa ", true),
                new ExamDraft.ChoiceDraft("Errada", false)));
    }

    private final class MemoryExams implements ExamRepository {

        @Override
        public UUID openExam(UUID cohortId, Actor actor, ExamDraft draft) {
            assertEquals(1, draft.attemptsAllowed());
            assertEquals(20, draft.minutes());
            assertEquals("Qual?", draft.questions().get(0).prompt());
            return opened;
        }

        @Override
        public void startExam(UUID cohortId, UUID examId, Actor actor) {
            assertEquals(opened, examId);
        }

        @Override
        public void takeExam(UUID cohortId, UUID examId, Actor actor, java.util.Map<UUID, UUID> answers) {
            assertEquals(opened, examId);
        }

        @Override
        public void removeExam(UUID cohortId, Actor actor, UUID examId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<skillforge.domain.cohort.Exam> inCohort(UUID cohortId, Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revise(
                UUID cohortId,
                Actor actor,
                UUID examId,
                String title,
                java.time.Instant dueAt,
                Integer minutes,
                int attemptsAllowed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reviseQuestions(
                UUID cohortId,
                Actor actor,
                UUID examId,
                java.util.List<skillforge.domain.studio.QuestionText> questions) {
            throw new UnsupportedOperationException();
        }

    }

    private static final class FinishedLessons implements LessonProgress {

        private boolean ready = true;

        @Override
        public boolean finished(UUID cohortId, String student) {
            return ready;
        }

    }

    private static final class EmptyBank implements BankRepository {

        @Override
        public UUID add(UUID courseId, Actor actor, String prompt, java.util.List<ExamDraft.ChoiceDraft> choices) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<skillforge.domain.studio.BankQuestion> list(UUID courseId, Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<ExamDraft.QuestionDraft> drafts(UUID cohortId, Actor actor, java.util.List<UUID> ids) {
            return java.util.List.of();
        }

    }

}
