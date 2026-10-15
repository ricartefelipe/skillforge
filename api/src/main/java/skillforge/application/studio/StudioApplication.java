package skillforge.application.studio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;
import skillforge.domain.studio.BankQuestion;
import skillforge.domain.studio.Hit;
import skillforge.domain.studio.QuestionText;
import skillforge.domain.studio.InboxNote;
import skillforge.domain.studio.PeerWork;
import skillforge.domain.studio.Resume;
import skillforge.domain.studio.StudioRepository;
import skillforge.domain.studio.StudyGroup;

public class StudioApplication {

    private final StudioRepository studio;

    public StudioApplication(StudioRepository studio) {
        this.studio = studio;
    }

    public Optional<Resume> resume(Actor actor) {
        return studio.resume(actor);
    }

    public void revisePractice(
            Actor actor,
            UUID cohortId,
            UUID practiceId,
            String rawTitle,
            String rawPrompt,
            Instant dueAt) {
        requireLead(actor);
        studio.revisePractice(
                cohortId,
                actor,
                practiceId,
                Names.require(rawTitle, Failure.BLANK_PRACTICE),
                Texts.require(rawPrompt, Failure.BLANK_PROMPT),
                dueAt);
    }

    public void reviseExam(
            Actor actor,
            UUID cohortId,
            UUID examId,
            String rawTitle,
            Instant dueAt,
            Integer minutes,
            Integer rawAttempts) {
        requireLead(actor);
        studio.reviseExam(
                cohortId,
                actor,
                examId,
                Names.require(rawTitle, Failure.BLANK_EXAM),
                dueAt,
                minutes(minutes),
                attempts(rawAttempts));
    }

    public void reviseAnnouncement(Actor actor, UUID cohortId, UUID announcementId, String rawTitle, String rawBody) {
        requireLead(actor);
        studio.reviseAnnouncement(
                cohortId,
                actor,
                announcementId,
                Names.require(rawTitle, Failure.BLANK_TITLE),
                Texts.require(rawBody, Failure.BLANK_PROMPT));
    }

    public void weigh(Actor actor, UUID cohortId, int practiceWeight, int examWeight) {
        requireLead(actor);
        if (practiceWeight < 1 || practiceWeight > 5 || examWeight < 1 || examWeight > 5) {
            throw new Rejection(Failure.WEIGHT);
        }
        studio.weigh(cohortId, actor, practiceWeight, examWeight);
    }

    public UUID addBankQuestion(Actor actor, UUID courseId, String rawPrompt, List<ExamDraft.ChoiceDraft> choices) {
        requireLead(actor);
        String prompt = Texts.require(rawPrompt, Failure.BLANK_QUESTION);
        return studio.addBankQuestion(courseId, actor, prompt, clean(choices));
    }

    public List<BankQuestion> bank(Actor actor, UUID courseId) {
        requireLead(actor);
        return studio.bank(courseId, actor);
    }

    public UUID openGroup(Actor actor, UUID cohortId, String rawName) {
        requireLead(actor);
        return studio.openGroup(cohortId, actor, Names.require(rawName, Failure.BLANK_TITLE));
    }

    public void addMember(Actor actor, UUID cohortId, UUID groupId, String rawStudent) {
        requireLead(actor);
        studio.addMember(cohortId, groupId, actor, Names.require(rawStudent, Failure.BLANK_STUDENT));
    }

    public List<StudyGroup> groups(Actor actor, UUID cohortId) {
        return studio.groups(cohortId, actor);
    }

    public void review(Actor actor, UUID cohortId, UUID practiceId, String rawAuthor, String rawComment, int mark) {
        actor.requireStudent();
        if (mark < 0 || mark > 10) {
            throw new Rejection(Failure.INVALID_GRADE);
        }
        studio.review(
                cohortId,
                practiceId,
                actor,
                Names.require(rawAuthor, Failure.BLANK_STUDENT),
                Texts.require(rawComment, Failure.BLANK_FEEDBACK),
                mark);
    }

    public List<PeerWork> peers(Actor actor, UUID cohortId, UUID practiceId) {
        return studio.peers(cohortId, practiceId, actor);
    }

    public List<InboxNote> inbox(Actor actor) {
        return studio.inbox(actor);
    }

    public void read(Actor actor, UUID id) {
        studio.read(actor, id);
    }

    public java.util.List<Hit> search(Actor actor, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2) {
            return java.util.List.of();
        }
        if (query.length() > 80) {
            throw new Rejection(Failure.TOO_LARGE);
        }
        return studio.search(actor, query);
    }

    public void saveMail(Actor actor, String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.length() < 3 || email.length() > Names.MAX || email.indexOf('@') < 1 || email.contains(" ")) {
            throw new Rejection(Failure.MAIL);
        }
        studio.saveMail(actor.username(), email);
    }

    public java.util.List<String> mail(Actor actor) {
        return studio.mail(actor.username());
    }

    public void reviseQuestions(Actor actor, UUID cohortId, UUID examId, java.util.List<QuestionText> questions) {
        requireLead(actor);
        if (questions == null || questions.isEmpty()) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        java.util.List<QuestionText> clean = new java.util.ArrayList<>();
        for (QuestionText question : questions) {
            if (question == null || question.choices() == null || question.choices().isEmpty()) {
                throw new Rejection(Failure.INVALID_EXAM);
            }
            java.util.List<QuestionText.ChoiceText> choices = new java.util.ArrayList<>();
            for (QuestionText.ChoiceText choice : question.choices()) {
                choices.add(new QuestionText.ChoiceText(
                        choice.id(),
                        Names.require(choice.label(), Failure.BLANK_CHOICE)));
            }
            String prompt = Texts.require(question.prompt(), Failure.BLANK_QUESTION);
            clean.add(new QuestionText(question.id(), prompt, choices));
        }
        studio.reviseQuestions(cohortId, actor, examId, clean);
    }

    public List<ExamDraft.QuestionDraft> bankDrafts(Actor actor, UUID cohortId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return studio.bankDrafts(cohortId, actor, ids);
    }

    public void tellCohort(UUID cohortId, String body) {
        studio.tellCohort(cohortId, body);
    }

    public void tell(String account, String body) {
        studio.tell(account, body);
    }

    private static void requireLead(Actor actor) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
    }

    private static Integer minutes(Integer minutes) {
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

    private static List<ExamDraft.ChoiceDraft> clean(List<ExamDraft.ChoiceDraft> choices) {
        if (choices == null || choices.size() < 2) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        int correct = 0;
        List<ExamDraft.ChoiceDraft> clean = new ArrayList<>();
        for (ExamDraft.ChoiceDraft choice : choices) {
            if (choice == null) {
                throw new Rejection(Failure.INVALID_EXAM);
            }
            if (choice.correct()) {
                correct++;
            }
            clean.add(new ExamDraft.ChoiceDraft(Names.require(choice.label(), Failure.BLANK_CHOICE), choice.correct()));
        }
        if (correct != 1) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        return clean;
    }

}
