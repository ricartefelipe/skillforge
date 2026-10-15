package skillforge.domain.cohort;

import java.util.Map;
import java.util.UUID;

import skillforge.domain.identity.Actor;

public interface ExamRepository {

    UUID openExam(UUID cohortId, Actor actor, ExamDraft draft);

    void takeExam(UUID cohortId, UUID examId, Actor actor, Map<UUID, UUID> answers);

    void removeExam(UUID cohortId, Actor actor, UUID examId);

}
