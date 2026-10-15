package skillforge.domain.cohort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import skillforge.domain.identity.Actor;

public interface PracticeRepository {

    UUID openPractice(
            UUID cohortId,
            Actor actor,
            String title,
            String prompt,
            Instant dueAt,
            List<CriterionDraft> criteria);

    void submit(UUID cohortId, UUID practiceId, Actor actor, String text);

    void removePractice(UUID cohortId, Actor actor, UUID practiceId);

    List<Practice> inCohort(UUID cohortId, Actor actor);

    void feedback(
            UUID cohortId,
            UUID practiceId,
            Actor actor,
            String student,
            String text,
            int grade,
            List<CriterionMark> marks);

    void revise(UUID cohortId, Actor actor, UUID practiceId, String title, String prompt, Instant dueAt);

}
