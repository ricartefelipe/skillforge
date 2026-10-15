package skillforge.application.notice;

import java.util.List;
import java.util.UUID;

import skillforge.domain.identity.Actor;
import skillforge.domain.notice.NoticeRepository;
import skillforge.domain.studio.InboxNote;

public class NoticeApplication {

    private final NoticeRepository notices;

    public NoticeApplication(NoticeRepository notices) {
        this.notices = notices;
    }

    public List<InboxNote> inbox(Actor actor) {
        return notices.inbox(actor);
    }

    public void read(Actor actor, UUID id) {
        notices.read(actor, id);
    }

}
