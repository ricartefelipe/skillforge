package skillforge.application.cohort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.cohort.ExamRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;

public class ExamApplication {

    private final ExamRepository exams;

    public ExamApplication(ExamRepository exams) {
        this.exams = exams;
    }

    public Opened open(
            Actor actor,
            UUID cohortId,
            String rawTitle,
            Instant dueAt,
            List<ExamDraft.QuestionDraft> questions) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        String title = Names.require(rawTitle, Failure.BLANK_EXAM);
        if (questions == null || questions.isEmpty()) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        List<ExamDraft.QuestionDraft> clean = new ArrayList<>();
        for (ExamDraft.QuestionDraft question : questions) {
            if (question == null || question.choices() == null || question.choices().size() < 2) {
                throw new Rejection(Failure.INVALID_EXAM);
            }
            String prompt = Texts.require(question.prompt(), Failure.BLANK_QUESTION);
            int correct = 0;
            List<ExamDraft.ChoiceDraft> choices = new ArrayList<>();
            for (ExamDraft.ChoiceDraft choice : question.choices()) {
                if (choice == null) {
                    throw new Rejection(Failure.INVALID_EXAM);
                }
                if (choice.correct()) {
                    correct++;
                }
                choices.add(new ExamDraft.ChoiceDraft(
                        Names.require(choice.label(), Failure.BLANK_CHOICE),
                        choice.correct()));
            }
            if (correct != 1) {
                throw new Rejection(Failure.INVALID_EXAM);
            }
            clean.add(new ExamDraft.QuestionDraft(prompt, choices));
        }
        return new Opened(exams.openExam(cohortId, actor, new ExamDraft(title, dueAt, clean)), title);
    }

    public void remove(Actor actor, UUID cohortId, UUID examId) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        exams.removeExam(cohortId, actor, examId);
    }

    public void take(Actor actor, UUID cohortId, UUID examId, Map<UUID, UUID> answers) {
        actor.requireStudent();
        if (answers == null) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        exams.takeExam(cohortId, examId, actor, answers);
    }

}
