package skillforge.domain.mail;

import skillforge.domain.studio.Mailbox;

public interface MailRepository {

    void save(String account, String email);

    Mailbox mailbox(String account);

}
