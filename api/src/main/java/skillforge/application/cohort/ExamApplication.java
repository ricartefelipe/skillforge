package skillforge.application.cohort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import skillforge.domain.bank.BankRepository;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.cohort.ExamRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.notice.NoticeRepository;
import skillforge.domain.studio.QuestionText;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;

public class ExamApplication {

    private final ExamRepository exams;

    private final BankRepository bank;

    private final NoticeRepository notices;

    public ExamApplication(ExamRepository exams, BankRepository bank, NoticeRepository notices) {
        this.exams = exams;
        this.bank = bank;
        this.notices = notices;
    }

    public Opened open(
            Actor actor,
            UUID cohortId,
            String rawTitle,
            Instant dueAt,
            Integer minutes,
            Integer attemptsAllowed,
            List<UUID> bank,
            List<ExamDraft.QuestionDraft> questions) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        String title = Names.require(rawTitle, Failure.BLANK_EXAM);
        List<ExamDraft.QuestionDraft> clean = new ArrayList<>(drafts(actor, cohortId, bank));
        if (questions != null) {
            clean.addAll(questionsOf(questions));
        }
        if (clean.isEmpty()) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        int attempts = attemptsAllowed == null ? 1 : attemptsAllowed;
        if (attempts < 1 || attempts > 5 || minutes != null && (minutes < 5 || minutes > 180)) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        UUID id = exams.openExam(cohortId, actor, new ExamDraft(title, dueAt, minutes, attempts, clean));
        notices.tellCohort(cohortId, "Nova prova: " + title);
        return new Opened(id, title);
    }

    public void start(Actor actor, UUID cohortId, UUID examId) {
        actor.requireStudent();
        exams.startExam(cohortId, examId, actor);
    }

    private static List<ExamDraft.QuestionDraft> questionsOf(List<ExamDraft.QuestionDraft> questions) {
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
        return List.copyOf(clean);
    }

    public void remove(Actor actor, UUID cohortId, UUID examId) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        exams.removeExam(cohortId, actor, examId);
    }

    public void revise(
            Actor actor,
            UUID cohortId,
            UUID examId,
            String rawTitle,
            Instant dueAt,
            Integer minutes,
            Integer rawAttempts) {
        requireLead(actor);
        exams.revise(
                cohortId,
                actor,
                examId,
                Names.require(rawTitle, Failure.BLANK_EXAM),
                dueAt,
                clock(minutes),
                attempts(rawAttempts));
    }

    public void reviseQuestions(Actor actor, UUID cohortId, UUID examId, List<QuestionText> questions) {
        requireLead(actor);
        if (questions == null || questions.isEmpty()) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        List<QuestionText> clean = new ArrayList<>();
        for (QuestionText question : questions) {
            if (question == null || question.choices() == null || question.choices().isEmpty()) {
                throw new Rejection(Failure.INVALID_EXAM);
            }
            List<QuestionText.ChoiceText> choices = new ArrayList<>();
            for (QuestionText.ChoiceText choice : question.choices()) {
                choices.add(new QuestionText.ChoiceText(
                        choice.id(),
                        Names.require(choice.label(), Failure.BLANK_CHOICE)));
            }
            String prompt = Texts.require(question.prompt(), Failure.BLANK_QUESTION);
            clean.add(new QuestionText(question.id(), prompt, choices));
        }
        exams.reviseQuestions(cohortId, actor, examId, clean);
    }

    public void take(Actor actor, UUID cohortId, UUID examId, Map<UUID, UUID> answers) {
        actor.requireStudent();
        if (answers == null) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        exams.takeExam(cohortId, examId, actor, answers);
    }

    private List<ExamDraft.QuestionDraft> drafts(Actor actor, UUID cohortId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return bank.drafts(cohortId, actor, ids);
    }

    private static void requireLead(Actor actor) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
    }

    private static Integer clock(Integer minutes) {
        if (minutes == null) {
            return null;
        }
        if (minutes < 5 || minutes > 180) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        return minutes;
    }

    private static int attempts(Integer rawAttempts) {
        int attempts = rawAttempts == null ? 1 : rawAttempts;
        if (attempts < 1 || attempts > 5) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        return attempts;
    }

}
