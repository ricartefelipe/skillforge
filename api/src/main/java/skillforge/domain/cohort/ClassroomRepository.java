package skillforge.domain.cohort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import skillforge.domain.identity.Actor;

public interface ClassroomRepository {

    UUID announce(UUID cohortId, Actor actor, String title, String body);

    UUID call(UUID cohortId, Actor actor, LocalDate heldOn, List<Presence> marks);

    Optional<Certificate> certificate(UUID cohortId, Actor actor);

    List<Announcement> announcements(UUID cohortId);

    List<Roll> rolls(UUID cohortId, Actor actor);

}
