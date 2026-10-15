package skillforge.domain.cohort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import skillforge.domain.identity.Actor;
import skillforge.domain.studio.QuestionText;

public interface ExamRepository {

    UUID openExam(UUID cohortId, Actor actor, ExamDraft draft);

    void startExam(UUID cohortId, UUID examId, Actor actor);

    void takeExam(UUID cohortId, UUID examId, Actor actor, Map<UUID, UUID> answers);

    void removeExam(UUID cohortId, Actor actor, UUID examId);

    List<Exam> inCohort(UUID cohortId, Actor actor);

    void revise(
            UUID cohortId,
            Actor actor,
            UUID examId,
            String title,
            Instant dueAt,
            Integer minutes,
            int attemptsAllowed);

    void reviseQuestions(UUID cohortId, Actor actor, UUID examId, List<QuestionText> questions);

}
