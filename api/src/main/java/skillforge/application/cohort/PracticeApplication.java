package skillforge.application.cohort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import skillforge.domain.cohort.CriterionDraft;
import skillforge.domain.cohort.CriterionMark;
import skillforge.domain.cohort.PracticeRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Marks;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.notice.NoticeRepository;
import skillforge.domain.shared.Texts;

public class PracticeApplication {

    private final PracticeRepository practices;

    private final NoticeRepository notices;

    public PracticeApplication(PracticeRepository practices, NoticeRepository notices) {
        this.practices = practices;
        this.notices = notices;
    }

    public Opened open(
            Actor actor,
            UUID cohortId,
            String rawTitle,
            String rawPrompt,
            Instant dueAt,
            List<CriterionDraft> criteria) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        String title = Names.require(rawTitle, Failure.BLANK_PRACTICE);
        String prompt = Texts.require(rawPrompt, Failure.BLANK_PROMPT);
        List<CriterionDraft> clean = new ArrayList<>();
        int sum = 0;
        if (criteria != null) {
            for (CriterionDraft criterion : criteria) {
                if (criterion.points() < 1 || criterion.points() > 10) {
                    throw new Rejection(Failure.RUBRIC);
                }
                sum += criterion.points();
                clean.add(new CriterionDraft(Names.require(criterion.name(), Failure.BLANK_TITLE), criterion.points()));
            }
        }
        if (sum > 10) {
            throw new Rejection(Failure.RUBRIC);
        }
        return new Opened(practices.openPractice(cohortId, actor, title, prompt, dueAt, clean), title);
    }

    public void remove(Actor actor, UUID cohortId, UUID practiceId) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        practices.removePractice(cohortId, actor, practiceId);
    }

    public void submit(Actor actor, UUID cohortId, UUID practiceId, String rawText) {
        actor.requireStudent();
        practices.submit(cohortId, practiceId, actor, Texts.require(rawText, Failure.BLANK_SUBMISSION));
    }

    public void feedback(
            Actor actor,
            UUID cohortId,
            UUID practiceId,
            String rawStudent,
            String rawText,
            Integer rawGrade,
            List<CriterionMark> marks) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        String student = Names.require(rawStudent, Failure.BLANK_STUDENT);
        List<CriterionMark> clean = marks == null ? List.of() : marks;
        int grade = clean.isEmpty() ? Marks.require(rawGrade) : clean.stream().mapToInt(CriterionMark::points).sum();
        practices.feedback(
                cohortId,
                practiceId,
                actor,
                student,
                Texts.require(rawText, Failure.BLANK_FEEDBACK),
                grade,
                clean);
        notices.tell(student, "A devolutiva da prática foi publicada.");
    }

    public void revise(
            Actor actor,
            UUID cohortId,
            UUID practiceId,
            String rawTitle,
            String rawPrompt,
            Instant dueAt) {
        requireLead(actor);
        practices.revise(
                cohortId,
                actor,
                practiceId,
                Names.require(rawTitle, Failure.BLANK_PRACTICE),
                Texts.require(rawPrompt, Failure.BLANK_PROMPT),
                dueAt);
    }

    private static void requireLead(Actor actor) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
    }

}
