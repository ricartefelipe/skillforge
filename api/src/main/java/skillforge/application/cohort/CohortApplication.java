package skillforge.application.cohort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import skillforge.domain.cohort.CohortDetail;
import skillforge.domain.cohort.CohortListing;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.cohort.Report;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Marks;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;

public class CohortApplication {

    private final CohortRepository cohorts;

    public CohortApplication(CohortRepository cohorts) {
        this.cohorts = cohorts;
    }

    public List<CohortListing> list(Actor actor) {
        return cohorts.visible(actor);
    }

    public CohortDetail page(Actor actor, UUID id) {
        return cohorts.page(id, actor).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public Opened open(Actor actor, UUID courseId, String rawName, String rawTeacher) {
        actor.requireAdministration();
        String name = Names.require(rawName, Failure.BLANK_COHORT);
        String teacher = Names.require(rawTeacher, Failure.BLANK_TEACHER);
        if (courseId == null) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return new Opened(cohorts.open(courseId, name, teacher), name);
    }

    public void enroll(Actor actor, UUID cohortId, String rawStudent) {
        actor.requireAdministration();
        cohorts.enroll(cohortId, Names.require(rawStudent, Failure.BLANK_STUDENT));
    }

    public void complete(Actor actor, UUID cohortId, UUID lessonId) {
        actor.requireStudent();
        cohorts.complete(cohortId, lessonId, actor.username());
    }

    public Opened openPractice(Actor actor, UUID cohortId, String rawTitle, String rawPrompt) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        String title = Names.require(rawTitle, Failure.BLANK_PRACTICE);
        String prompt = Texts.require(rawPrompt, Failure.BLANK_PROMPT);
        return new Opened(cohorts.openPractice(cohortId, actor, title, prompt), title);
    }

    public void submit(Actor actor, UUID cohortId, UUID practiceId, String rawText) {
        actor.requireStudent();
        cohorts.submit(cohortId, practiceId, actor, Texts.require(rawText, Failure.BLANK_SUBMISSION));
    }

    public void feedback(
            Actor actor,
            UUID cohortId,
            UUID practiceId,
            String rawStudent,
            String rawText,
            Integer rawGrade) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        String student = Names.require(rawStudent, Failure.BLANK_STUDENT);
        cohorts.feedback(
                cohortId,
                practiceId,
                actor,
                student,
                Texts.require(rawText, Failure.BLANK_FEEDBACK),
                Marks.require(rawGrade));
    }

    public List<Report> report(Actor actor) {
        return cohorts.report(actor);
    }

    public Opened openExam(Actor actor, UUID cohortId, String rawTitle, List<ExamDraft.QuestionDraft> questions) {
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
        return new Opened(cohorts.openExam(cohortId, actor, new ExamDraft(title, clean)), title);
    }

    public void takeExam(Actor actor, UUID cohortId, UUID examId, Map<UUID, UUID> answers) {
        actor.requireStudent();
        if (answers == null) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        cohorts.takeExam(cohortId, examId, actor, answers);
    }

    public record Opened(UUID id, String name) {
    }

}
