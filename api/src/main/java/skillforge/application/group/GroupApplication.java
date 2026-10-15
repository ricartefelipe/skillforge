package skillforge.application.group;

import java.util.List;
import java.util.UUID;

import skillforge.domain.group.GroupRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;
import skillforge.domain.studio.PeerWork;
import skillforge.domain.studio.StudyGroup;

public class GroupApplication {

    private final GroupRepository groups;

    public GroupApplication(GroupRepository groups) {
        this.groups = groups;
    }

    public UUID open(Actor actor, UUID cohortId, String rawName) {
        requireLead(actor);
        return groups.open(cohortId, actor, Names.require(rawName, Failure.BLANK_TITLE));
    }

    public void addMember(Actor actor, UUID cohortId, UUID groupId, String rawStudent) {
        requireLead(actor);
        groups.addMember(cohortId, groupId, actor, Names.require(rawStudent, Failure.BLANK_STUDENT));
    }

    public List<StudyGroup> groups(Actor actor, UUID cohortId) {
        return groups.groups(cohortId, actor);
    }

    public void review(Actor actor, UUID cohortId, UUID practiceId, String rawAuthor, String rawComment, int mark) {
        actor.requireStudent();
        if (mark < 0 || mark > 10) {
            throw new Rejection(Failure.INVALID_GRADE);
        }
        groups.review(
                cohortId,
                practiceId,
                actor,
                Names.require(rawAuthor, Failure.BLANK_STUDENT),
                Texts.require(rawComment, Failure.BLANK_FEEDBACK),
                mark);
    }

    public List<PeerWork> peers(Actor actor, UUID cohortId, UUID practiceId) {
        return groups.peers(cohortId, practiceId, actor);
    }

    private static void requireLead(Actor actor) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
    }

}
