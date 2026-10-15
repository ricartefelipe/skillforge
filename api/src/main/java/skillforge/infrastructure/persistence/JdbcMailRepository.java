package skillforge.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import skillforge.domain.mail.MailRepository;
import skillforge.domain.studio.Mailbox;

@Component
class JdbcMailRepository implements MailRepository {

    private final CohortGate gate;

    JdbcMailRepository(CohortGate gate) {
        this.gate = gate;
    }

    @Override
    public void save(String account, String email) {
        gate.ensure();
        gate.jdbc.update("""
                INSERT INTO account_mail (account, email) VALUES (?, ?)
                ON CONFLICT (account) DO UPDATE SET email = EXCLUDED.email
                """, account, email);
    }

    @Override
    public Mailbox mailbox(String account) {
        gate.ensure();
        List<String> addresses = gate.jdbc.query(
                "SELECT email FROM account_mail WHERE account = ?",
                (rs, row) -> rs.getString("email"),
                account);
        List<String> sent = gate.jdbc.query(
                "SELECT body FROM mail_outbox WHERE account = ? ORDER BY created_at DESC LIMIT 20",
                (rs, row) -> rs.getString("body"),
                account);
        return new Mailbox(addresses.isEmpty() ? null : addresses.get(0), sent);
    }

}
