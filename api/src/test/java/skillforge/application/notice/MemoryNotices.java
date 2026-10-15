package skillforge.application.notice;

import java.util.List;
import java.util.UUID;

import skillforge.domain.identity.Actor;
import skillforge.domain.notice.NoticeRepository;
import skillforge.domain.studio.InboxNote;

public class MemoryNotices implements NoticeRepository {

    public String notice;

    public UUID cohortNotice;

    @Override
    public void tell(String account, String body) {
        notice = body;
    }

    @Override
    public void tellCohort(UUID cohortId, String body) {
        cohortNotice = cohortId;
        notice = body;
    }

    @Override
    public List<InboxNote> inbox(Actor actor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void read(Actor actor, UUID id) {
        throw new UnsupportedOperationException();
    }

}
