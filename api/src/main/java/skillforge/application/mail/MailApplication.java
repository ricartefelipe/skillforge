package skillforge.application.mail;

import skillforge.domain.identity.Actor;
import skillforge.domain.mail.MailRepository;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.Mailbox;

public class MailApplication {

    private final MailRepository mail;

    public MailApplication(MailRepository mail) {
        this.mail = mail;
    }

    public void save(Actor actor, String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.length() < 3 || email.length() > Names.MAX || email.indexOf('@') < 1 || email.contains(" ")) {
            throw new Rejection(Failure.MAIL);
        }
        mail.save(actor.username(), email);
    }

    public Mailbox mailbox(Actor actor) {
        return mail.mailbox(actor.username());
    }

}
