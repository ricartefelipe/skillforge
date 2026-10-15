package skillforge.domain.cohort;

import java.util.Set;
import java.util.UUID;

import skillforge.domain.identity.Actor;

public interface CourseAccess {

    boolean teaches(String teacher, UUID courseId);

    Set<UUID> courseIds(Actor actor);

}
