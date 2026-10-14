package skillforge.application.cohort;

import java.util.List;
import java.util.UUID;

import skillforge.domain.cohort.CohortDetail;
import skillforge.domain.cohort.CohortListing;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;

public class CohortApplication {

    private final CohortRepository cohorts;

    public CohortApplication(CohortRepository cohorts) {
        this.cohorts = cohorts;
    }

    public List<CohortListing> list(Actor actor) {
        return cohorts.visible(actor);
    }

    public CohortDetail page(Actor actor, UUID id) {
        return cohorts.page(id, actor).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public Opened open(Actor actor, UUID courseId, String rawName, String rawTeacher) {
        actor.requireAdministration();
        String name = Names.require(rawName, Failure.BLANK_COHORT);
        String teacher = Names.require(rawTeacher, Failure.BLANK_TEACHER);
        if (courseId == null) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return new Opened(cohorts.open(courseId, name, teacher), name);
    }

    public void enroll(Actor actor, UUID cohortId, String rawStudent) {
        actor.requireAdministration();
        cohorts.enroll(cohortId, Names.require(rawStudent, Failure.BLANK_STUDENT));
    }

    public void complete(Actor actor, UUID cohortId, UUID lessonId) {
        actor.requireStudent();
        cohorts.complete(cohortId, lessonId, actor.username());
    }

    public record Opened(UUID id, String name) {
    }

}
