package skillforge.domain.bank;

import java.util.List;
import java.util.UUID;

import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.studio.BankQuestion;

public interface BankRepository {

    UUID add(UUID courseId, Actor actor, String prompt, List<ExamDraft.ChoiceDraft> choices);

    List<BankQuestion> list(UUID courseId, Actor actor);

    List<ExamDraft.QuestionDraft> drafts(UUID cohortId, Actor actor, List<UUID> ids);

}
