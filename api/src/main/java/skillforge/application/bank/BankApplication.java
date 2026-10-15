package skillforge.application.bank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import skillforge.domain.bank.BankRepository;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;
import skillforge.domain.studio.BankQuestion;

public class BankApplication {

    private final BankRepository bank;

    public BankApplication(BankRepository bank) {
        this.bank = bank;
    }

    public UUID add(Actor actor, UUID courseId, String rawPrompt, List<ExamDraft.ChoiceDraft> choices) {
        requireLead(actor);
        return bank.add(courseId, actor, Texts.require(rawPrompt, Failure.BLANK_QUESTION), clean(choices));
    }

    public List<BankQuestion> list(Actor actor, UUID courseId) {
        requireLead(actor);
        return bank.list(courseId, actor);
    }

    public List<ExamDraft.QuestionDraft> drafts(Actor actor, UUID cohortId, List<UUID> ids) {
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
