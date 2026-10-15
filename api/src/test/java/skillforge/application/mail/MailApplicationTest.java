package skillforge.application.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.mail.MailRepository;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.Mailbox;

class MailApplicationTest {

    private final MemoryMail store = new MemoryMail();

    private final MailApplication mail = new MailApplication(store);

    private final Actor student = new Actor(Role.ALUNO, "aluno");

    @Test
    void mailNeedsAnAddressWithOneAtSign() {
        mail.save(student, " aluno@example.com ");
        assertEquals("aluno@example.com", store.savedMail);
        assertEquals(
                "mail",
                assertThrows(Rejection.class, () -> mail.save(student, "sem-arroba")).failure().wire());
        assertEquals(
                "mail",
                assertThrows(Rejection.class, () -> mail.save(student, "a b@example.com")).failure().wire());
    }

    private static final class MemoryMail implements MailRepository {

        private String savedMail;

        @Override
        public void save(String account, String email) {
            savedMail = email;
        }

        @Override
        public Mailbox mailbox(String account) {
            return new Mailbox(savedMail, List.of());
        }

    }

}
