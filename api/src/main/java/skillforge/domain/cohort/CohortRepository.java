package skillforge.domain.cohort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import skillforge.domain.identity.Actor;

public interface CohortRepository {

    UUID open(UUID courseId, String name, String teacher);

    void enroll(UUID cohortId, String student);

    List<CohortListing> visible(Actor actor);

    Optional<CohortDetail> page(UUID id, Actor actor);

    void complete(UUID cohortId, UUID lessonId, String student);

}
