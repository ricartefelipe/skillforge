package skillforge.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import skillforge.domain.cohort.CohortAccess;
import skillforge.domain.group.GroupRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.notice.NoticeRepository;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.PeerNote;
import skillforge.domain.studio.PeerWork;
import skillforge.domain.studio.StudyGroup;

@Component
class JdbcGroupRepository implements GroupRepository {

    private final CohortGate gate;

    private final NoticeRepository notices;

    JdbcGroupRepository(CohortGate gate, NoticeRepository notices) {
        this.gate = gate;
        this.notices = notices;
    }

    @Override
    public UUID open(UUID cohortId, Actor actor, String name) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            UUID id = UUID.randomUUID();
            gate.jdbc.update(
                    "INSERT INTO study_group (id, cohort_id, name) VALUES (?, ?, ?)",
                    id,
                    cohortId,
                    name);
            return id;
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_GROUP);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void addMember(UUID cohortId, UUID groupId, Actor actor, String student) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            if (!groupInCohort(cohortId, groupId) || !gate.enrolled(cohortId, student)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer elsewhere = gate.jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM study_member
                    JOIN study_group ON study_group.id = study_member.group_id
                    WHERE study_group.cohort_id = ? AND study_member.student = ?
                    """, Integer.class, cohortId, student);
            if (elsewhere != null && elsewhere > 0) {
                throw new Rejection(Failure.GROUPED);
            }
            gate.jdbc.update("INSERT INTO study_member (group_id, student) VALUES (?, ?)", groupId, student);
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.GROUPED);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<StudyGroup> groups(UUID cohortId, Actor actor) {
        gate.ensure();
        try {
            requireSeen(cohortId, actor);
            List<UUID> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            gate.jdbc.query(
                    "SELECT id, name FROM study_group WHERE cohort_id = ? ORDER BY name",
                    (rs, row) -> {
                        ids.add(rs.getObject("id", UUID.class));
                        names.add(rs.getString("name"));
                        return null;
                    },
                    cohortId);
            List<StudyGroup> groups = new ArrayList<>();
            for (int index = 0; index < ids.size(); index++) {
                UUID id = ids.get(index);
                List<String> members = gate.jdbc.query(
                        "SELECT student FROM study_member WHERE group_id = ? ORDER BY student",
                        (rs, row) -> rs.getString("student"),
                        id);
                groups.add(new StudyGroup(id, names.get(index), members));
            }
            return List.copyOf(groups);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void review(
            UUID cohortId,
            UUID practiceId,
            Actor reviewer,
            String author,
            String comment,
            int mark) {
        gate.ensure();
        try {
            reviewer.requireStudent();
            if (!gate.enrolled(cohortId, reviewer.username()) || !practiceInCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            if (reviewer.username().equals(author) || !sameGroup(cohortId, reviewer.username(), author)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer submitted = gate.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM submission WHERE practice_id = ? AND student = ?",
                    Integer.class,
                    practiceId,
                    author);
            if (submitted == null || submitted == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            gate.jdbc.update(
                    "INSERT INTO peer_review (practice_id, author, reviewer, comment, mark) VALUES (?, ?, ?, ?, ?)",
                    practiceId,
                    author,
                    reviewer.username(),
                    comment,
                    mark);
            notices.tell(author, "Um colega avaliou a sua entrega.");
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.ALREADY_REVIEWED);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<PeerWork> peers(UUID cohortId, UUID practiceId, Actor actor) {
        gate.ensure();
        try {
            requireSeen(cohortId, actor);
            if (!practiceInCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Map<String, String> texts = new LinkedHashMap<>();
            if (actor.role() == Role.ALUNO) {
                List<String> peers = classmates(cohortId, actor.username());
                if (peers.isEmpty()) {
                    return List.of();
                }
                gate.jdbc.query(
                        "SELECT student, body FROM submission WHERE practice_id = ? ORDER BY student",
                        (rs, row) -> {
                            String student = rs.getString("student");
                            if (peers.contains(student)) {
                                texts.put(student, rs.getString("body"));
                            }
                            return null;
                        },
                        practiceId);
            }
            else {
                gate.jdbc.query(
                        "SELECT student, body FROM submission WHERE practice_id = ? ORDER BY student",
                        (rs, row) -> {
                            texts.put(rs.getString("student"), rs.getString("body"));
                            return null;
                        },
                        practiceId);
            }
            Map<String, List<PeerNote>> notes = notesOf(practiceId, actor);
            List<PeerWork> works = new ArrayList<>();
            for (Map.Entry<String, String> entry : texts.entrySet()) {
                works.add(new PeerWork(
                        entry.getKey(),
                        entry.getValue(),
                        notes.getOrDefault(entry.getKey(), List.of())));
            }
            return List.copyOf(works);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private void requireSeen(UUID cohortId, Actor actor) {
        List<String> teachers = gate.jdbc.query(
                "SELECT teacher FROM cohort WHERE id = ?",
                (rs, row) -> rs.getString("teacher"),
                cohortId);
        boolean seen = !teachers.isEmpty() && CohortAccess.canSee(
                actor.role(),
                teachers.get(0),
                actor.username(),
                gate.enrolled(cohortId, actor.username()));
        if (!seen) {
            throw new Rejection(Failure.NOT_FOUND);
        }
    }

    private boolean practiceInCohort(UUID cohortId, UUID practiceId) {
        Integer count = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice WHERE id = ? AND cohort_id = ?",
                Integer.class,
                practiceId,
                cohortId);
        return count != null && count > 0;
    }

    private boolean groupInCohort(UUID cohortId, UUID groupId) {
        Integer count = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_group WHERE id = ? AND cohort_id = ?",
                Integer.class,
                groupId,
                cohortId);
        return count != null && count > 0;
    }

    private boolean sameGroup(UUID cohortId, String reviewer, String author) {
        Integer count = gate.jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM study_member mine
                JOIN study_member theirs ON theirs.group_id = mine.group_id
                JOIN study_group ON study_group.id = mine.group_id
                WHERE study_group.cohort_id = ? AND mine.student = ? AND theirs.student = ?
                """, Integer.class, cohortId, reviewer, author);
        return count != null && count > 0;
    }

    private List<String> classmates(UUID cohortId, String student) {
        return gate.jdbc.query("""
                SELECT theirs.student
                FROM study_member mine
                JOIN study_member theirs ON theirs.group_id = mine.group_id
                JOIN study_group ON study_group.id = mine.group_id
                WHERE study_group.cohort_id = ? AND mine.student = ? AND theirs.student <> ?
                """, (rs, row) -> rs.getString("student"), cohortId, student, student);
    }

    private Map<String, List<PeerNote>> notesOf(UUID practiceId, Actor actor) {
        Map<String, List<PeerNote>> notes = new LinkedHashMap<>();
        gate.jdbc.query(
                "SELECT author, reviewer, comment, mark FROM peer_review WHERE practice_id = ? ORDER BY reviewer",
                (rs, row) -> {
                    String author = rs.getString("author");
                    String reviewer = rs.getString("reviewer");
                    boolean visible = actor.role() != Role.ALUNO
                            || reviewer.equals(actor.username())
                            || author.equals(actor.username());
                    if (visible) {
                        notes.computeIfAbsent(author, key -> new ArrayList<>()).add(new PeerNote(
                                author,
                                reviewer,
                                rs.getString("comment"),
                                rs.getInt("mark")));
                    }
                    return null;
                },
                practiceId);
        return notes;
    }

}
