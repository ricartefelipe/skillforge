package skillforge.domain.cohort;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import skillforge.domain.identity.Actor;

public interface CohortRepository {

    UUID open(UUID courseId, String name, String teacher);

    void enroll(UUID cohortId, String student);

    List<CohortListing> visible(Actor actor);

    Optional<CohortDetail> page(UUID id, Actor actor);

    void complete(UUID cohortId, UUID lessonId, String student);

    UUID openPractice(UUID cohortId, Actor actor, String title, String prompt);

    void submit(UUID cohortId, UUID practiceId, Actor actor, String text);

    void feedback(UUID cohortId, UUID practiceId, Actor actor, String student, String text, int grade);

    boolean teaches(String teacher, UUID courseId);

    Set<UUID> courseIds(Actor actor);

    List<Report> report(Actor actor);

    UUID openExam(UUID cohortId, Actor actor, ExamDraft draft);

    void takeExam(UUID cohortId, UUID examId, Actor actor, java.util.Map<UUID, UUID> answers);

}
