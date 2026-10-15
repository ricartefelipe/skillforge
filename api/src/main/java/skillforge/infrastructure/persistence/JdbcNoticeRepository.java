package skillforge.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import skillforge.domain.identity.Actor;
import skillforge.domain.notice.NoticeRepository;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.InboxNote;

@Component
class JdbcNoticeRepository implements NoticeRepository {

    private final CohortGate gate;

    private final MailDispatch mail;

    JdbcNoticeRepository(CohortGate gate, MailDispatch mail) {
        this.gate = gate;
        this.mail = mail;
    }

    @Override
    public void tell(String account, String body) {
        gate.ensure();
        try {
            gate.jdbc.update(
                    "INSERT INTO inbox (id, account, body, created_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    account,
                    body,
                    Timestamp.from(Instant.now()));
            mail.offer(account, body);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void tellCohort(UUID cohortId, String body) {
        gate.ensure();
        try {
            List<String> accounts = gate.jdbc.query(
                    "SELECT student FROM enrollment WHERE cohort_id = ?",
                    (rs, row) -> rs.getString("student"),
                    cohortId);
            accounts.addAll(gate.jdbc.query(
                    "SELECT teacher FROM cohort WHERE id = ?",
                    (rs, row) -> rs.getString("teacher"),
                    cohortId));
            for (String account : accounts) {
                tell(account, body);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<InboxNote> inbox(Actor actor) {
        gate.ensure();
        try {
            return gate.jdbc.query("""
                    SELECT id, body, created_at, read_at
                    FROM inbox
                    WHERE account = ?
                    ORDER BY created_at DESC
                    """, (rs, row) -> new InboxNote(
                    rs.getObject("id", UUID.class),
                    rs.getString("body"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("read_at") != null), actor.username());
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void read(Actor actor, UUID id) {
        gate.ensure();
        try {
            int updated = gate.jdbc.update(
                    "UPDATE inbox SET read_at = ? WHERE id = ? AND account = ? AND read_at IS NULL",
                    Timestamp.from(Instant.now()),
                    id,
                    actor.username());
            if (updated == 0) {
                Integer own = gate.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inbox WHERE id = ? AND account = ?",
                        Integer.class,
                        id,
                        actor.username());
                if (own == null || own == 0) {
                    throw new Rejection(Failure.NOT_FOUND);
                }
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

}
