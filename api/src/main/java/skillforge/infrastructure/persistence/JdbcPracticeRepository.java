package skillforge.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import skillforge.domain.cohort.Criterion;
import skillforge.domain.cohort.CriterionDraft;
import skillforge.domain.cohort.CriterionMark;
import skillforge.domain.cohort.Delivery;
import skillforge.domain.cohort.Practice;
import skillforge.domain.cohort.PracticeRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

@Component
class JdbcPracticeRepository implements PracticeRepository {

    private final CohortGate gate;

    JdbcPracticeRepository(CohortGate gate) {
        this.gate = gate;
    }

    @Override
    public UUID openPractice(
            UUID cohortId,
            Actor actor,
            String title,
            String prompt,
            java.time.Instant dueAt,
            List<CriterionDraft> criteria) {
        gate.ensure();
        UUID id = UUID.randomUUID();
        try {
            gate.jdbc.update(
                    "INSERT INTO practice (id, cohort_id, title, prompt, due_at) VALUES (?, ?, ?, ?, ?)",
                    id,
                    gate.requireLead(cohortId, actor).id(),
                    title,
                    prompt,
                    dueAt == null ? null : java.sql.Timestamp.from(dueAt));
            for (CriterionDraft criterion : criteria) {
                gate.jdbc.update(
                        "INSERT INTO criterion (id, practice_id, name, points) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(),
                        id,
                        criterion.name(),
                        criterion.points());
            }
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_PRACTICE);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
        return id;
    }

    @Override
    public void submit(UUID cohortId, UUID practiceId, Actor actor, String text) {
        gate.ensure();
        try {
            if (!gate.enrolled(cohortId, actor.username()) || !inCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            CohortGate.refuseIfClosed(due(practiceId));
            String feedback = currentFeedback(practiceId, actor.username());
            if (feedback == null && !submissionExists(practiceId, actor.username())) {
                gate.jdbc.update(
                        "INSERT INTO submission (practice_id, student, body) VALUES (?, ?, ?)",
                        practiceId,
                        actor.username(),
                        text);
                return;
            }
            if (feedback != null) {
                throw new Rejection(Failure.ALREADY_RETURNED);
            }
            gate.jdbc.update(
                    "UPDATE submission SET body = ? WHERE practice_id = ? AND student = ?",
                    text,
                    practiceId,
                    actor.username());
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void feedback(
            UUID cohortId,
            UUID practiceId,
            Actor actor,
            String student,
            String text,
            int grade,
            List<CriterionMark> marks) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            if (!inCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            storeMarks(practiceId, student, marks, grade);
            int updated = gate.jdbc.update(
                    "UPDATE submission SET feedback = ?, grade = ? WHERE practice_id = ? AND student = ?",
                    text,
                    grade,
                    practiceId,
                    student);
            if (updated == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    @Transactional
    public void removePractice(UUID cohortId, Actor actor, UUID practiceId) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            if (!inCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            gate.jdbc.update(
                    "DELETE FROM criterion_mark WHERE criterion_id IN (SELECT id FROM criterion WHERE practice_id = ?)",
                    practiceId);
            gate.jdbc.update("DELETE FROM criterion WHERE practice_id = ?", practiceId);
            gate.jdbc.update("DELETE FROM peer_review WHERE practice_id = ?", practiceId);
            gate.jdbc.update("DELETE FROM submission WHERE practice_id = ?", practiceId);
            gate.jdbc.update("DELETE FROM practice WHERE id = ?", practiceId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<Practice> inCohort(UUID cohortId, Actor actor) {
        gate.ensure();
        List<Seed> seeds = gate.jdbc.query(
                "SELECT id, title, prompt, due_at FROM practice WHERE cohort_id = ? ORDER BY title",
                (rs, row) -> new Seed(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("prompt"),
                        CohortGate.instant(rs.getTimestamp("due_at"))),
                cohortId);
        if (seeds.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Delivery>> deliveries = new LinkedHashMap<>();
        for (Seed seed : seeds) {
            deliveries.put(seed.id(), new ArrayList<>());
        }
        loadDeliveries(deliveries, cohortId, actor.role() == Role.ALUNO ? actor.username() : null);
        List<Practice> practices = new ArrayList<>();
        for (Seed seed : seeds) {
            practices.add(new Practice(
                    seed.id(),
                    seed.title(),
                    seed.prompt(),
                    seed.dueAt(),
                    criteriaOf(seed.id()),
                    deliveries.get(seed.id())));
        }
        return List.copyOf(practices);
    }

    private boolean inCohort(UUID cohortId, UUID practiceId) {
        Integer count = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice WHERE id = ? AND cohort_id = ?",
                Integer.class,
                practiceId,
                cohortId);
        return count != null && count > 0;
    }

    private boolean submissionExists(UUID practiceId, String student) {
        Integer count = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM submission WHERE practice_id = ? AND student = ?",
                Integer.class,
                practiceId,
                student);
        return count != null && count > 0;
    }

    private String currentFeedback(UUID practiceId, String student) {
        List<String> rows = gate.jdbc.query(
                "SELECT feedback FROM submission WHERE practice_id = ? AND student = ?",
                (rs, row) -> rs.getString("feedback"),
                practiceId,
                student);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private java.time.Instant due(UUID practiceId) {
        return CohortGate.instant(gate.jdbc.queryForObject(
                "SELECT due_at FROM practice WHERE id = ?",
                java.sql.Timestamp.class,
                practiceId));
    }

    private List<Criterion> criteriaOf(UUID practiceId) {
        return gate.jdbc.query(
                "SELECT id, name, points FROM criterion WHERE practice_id = ? ORDER BY name",
                (rs, row) -> new Criterion(rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("points")),
                practiceId);
    }

    private void storeMarks(UUID practiceId, String student, List<CriterionMark> marks, int grade) {
        List<Criterion> criteria = criteriaOf(practiceId);
        if (criteria.isEmpty()) {
            if (marks != null && !marks.isEmpty()) {
                throw new Rejection(Failure.RUBRIC);
            }
            return;
        }
        if (marks == null || marks.size() != criteria.size()) {
            throw new Rejection(Failure.RUBRIC);
        }
        int sum = 0;
        for (Criterion criterion : criteria) {
            CriterionMark mark = null;
            for (CriterionMark candidate : marks) {
                if (criterion.id().equals(candidate.criterionId())) {
                    mark = candidate;
                }
            }
            if (mark == null || mark.points() < 0 || mark.points() > criterion.points()) {
                throw new Rejection(Failure.RUBRIC);
            }
            sum += mark.points();
            gate.jdbc.update(
                    "INSERT INTO criterion_mark (criterion_id, student, points) VALUES (?, ?, ?)",
                    criterion.id(),
                    student,
                    mark.points());
        }
        if (sum != grade) {
            throw new Rejection(Failure.RUBRIC);
        }
    }

    private void loadDeliveries(Map<UUID, List<Delivery>> deliveries, UUID cohortId, String student) {
        String sql = """
                SELECT submission.practice_id, submission.student, submission.body,
                       submission.feedback, submission.grade
                FROM submission
                JOIN practice ON practice.id = submission.practice_id
                WHERE practice.cohort_id = ?
                """;
        if (student == null) {
            gate.jdbc.query(sql + " ORDER BY submission.student", (rs, row) -> keep(deliveries, rs), cohortId);
            return;
        }
        gate.jdbc.query(
                sql + " AND submission.student = ? ORDER BY submission.student",
                (rs, row) -> keep(deliveries, rs),
                cohortId,
                student);
    }

    private static Object keep(Map<UUID, List<Delivery>> deliveries, ResultSet rs) throws SQLException {
        List<Delivery> found = deliveries.get(rs.getObject("practice_id", UUID.class));
        if (found != null) {
            found.add(new Delivery(
                    rs.getString("student"),
                    rs.getString("body"),
                    rs.getString("feedback"),
                    rs.getObject("grade", Integer.class)));
        }
        return null;
    }

    private record Seed(UUID id, String title, String prompt, java.time.Instant dueAt) {
    }

    @Override
    public void revise(UUID cohortId, Actor actor, UUID practiceId, String title, String prompt, Instant dueAt) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            Integer owned = gate.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM practice WHERE id = ? AND cohort_id = ?",
                    Integer.class,
                    practiceId,
                    cohortId);
            if (owned == null || owned == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer submissions = gate.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM submission WHERE practice_id = ?",
                    Integer.class,
                    practiceId);
            if (submissions != null && submissions > 0) {
                throw new Rejection(Failure.LOCKED);
            }
            gate.jdbc.update(
                    "UPDATE practice SET title = ?, prompt = ?, due_at = ? WHERE id = ?",
                    title,
                    prompt,
                    dueAt == null ? null : Timestamp.from(dueAt),
                    practiceId);
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_PRACTICE);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

}
