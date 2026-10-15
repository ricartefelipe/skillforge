package skillforge.domain.notice;

import java.util.List;
import java.util.UUID;

import skillforge.domain.identity.Actor;
import skillforge.domain.studio.InboxNote;

public interface NoticeRepository {

    void tell(String account, String body);

    void tellCohort(UUID cohortId, String body);

    List<InboxNote> inbox(Actor actor);

    void read(Actor actor, UUID id);

}
