package skillforge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.cohort.CohortAccess;
import skillforge.domain.cohort.CohortSummary;
import skillforge.domain.identity.Actor;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

@Component
class CohortGate {

    static final String LIST = """
            SELECT cohort.id, cohort.name, cohort.teacher, course.id AS course_id, course.name AS course_name
            FROM cohort
            JOIN course ON course.id = cohort.course_id
            """;

    final Schema schema;

    final JdbcTemplate jdbc;

    final CourseRepository courses;

    CohortGate(Schema schema, JdbcTemplate jdbc, CourseRepository courses) {
        this.schema = schema;
        this.jdbc = jdbc;
        this.courses = courses;
    }

    void ensure() {
        schema.ensure();
    }

    List<CohortSummary> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> new CohortSummary(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_name"),
                rs.getString("teacher")), args);
    }

    CohortSummary requireLead(UUID cohortId, Actor actor) {
        List<CohortSummary> rows = query(LIST + " WHERE cohort.id = ?", cohortId);
        if (rows.isEmpty() || !CohortAccess.canLead(actor.role(), rows.get(0).teacher(), actor.username())) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return rows.get(0);
    }

    boolean enrolled(UUID cohortId, String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE cohort_id = ? AND student = ?",
                Integer.class,
                cohortId,
                username);
        return count != null && count > 0;
    }

    boolean exists(UUID id) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cohort WHERE id = ?", Integer.class, id);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    boolean allowed(CohortSummary row, Actor actor) {
        return CohortAccess.canSee(actor.role(), row.teacher(), actor.username(), enrolled(row.id(), actor.username()));
    }

    boolean lessonInCohort(UUID cohortId, UUID lessonId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                JOIN cohort ON cohort.course_id = module.course_id
                WHERE lesson.id = ? AND cohort.id = ?
                """, Integer.class, lessonId, cohortId);
        return count != null && count > 0;
    }

    int[] weights(UUID cohortId) {
        return jdbc.queryForObject(
                "SELECT practice_weight, exam_weight FROM cohort WHERE id = ?",
                (rs, row) -> new int[] {
                        rs.getObject("practice_weight") == null ? 1 : rs.getInt("practice_weight"),
                        rs.getObject("exam_weight") == null ? 1 : rs.getInt("exam_weight")
                },
                cohortId);
    }

    static void refuseIfClosed(java.time.Instant dueAt) {
        if (dueAt != null && !dueAt.isAfter(java.time.Instant.now())) {
            throw new Rejection(Failure.PAST_DUE);
        }
    }

    static java.time.Instant instant(java.sql.Timestamp dueAt) {
        return dueAt == null ? null : dueAt.toInstant();
    }

}
