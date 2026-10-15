package skillforge.domain.group;

import java.util.List;
import java.util.UUID;

import skillforge.domain.identity.Actor;
import skillforge.domain.studio.PeerWork;
import skillforge.domain.studio.StudyGroup;

public interface GroupRepository {

    UUID open(UUID cohortId, Actor actor, String name);

    void addMember(UUID cohortId, UUID groupId, Actor actor, String student);

    List<StudyGroup> groups(UUID cohortId, Actor actor);

    void review(UUID cohortId, UUID practiceId, Actor reviewer, String author, String comment, int mark);

    List<PeerWork> peers(UUID cohortId, UUID practiceId, Actor actor);

}
