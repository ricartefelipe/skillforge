package skillforge.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import skillforge.domain.cohort.Announcement;
import skillforge.domain.cohort.Certificate;
import skillforge.domain.cohort.ClassroomRepository;
import skillforge.domain.cohort.CohortSummary;
import skillforge.domain.cohort.Presence;
import skillforge.domain.cohort.Roll;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

@Component
class JdbcClassroomRepository implements ClassroomRepository {

    private final CohortGate gate;

    JdbcClassroomRepository(CohortGate gate) {
        this.gate = gate;
    }

    @Override
    public List<Announcement> announcements(UUID cohortId) {
        gate.ensure();
        try {
            return gate.jdbc.query(
                    "SELECT id, title, body FROM announcement WHERE cohort_id = ? ORDER BY title",
                    (rs, row) -> new Announcement(
                            rs.getObject("id", UUID.class),
                            rs.getString("title"),
                            rs.getString("body")),
                    cohortId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<Roll> rolls(UUID cohortId, Actor actor) {
        gate.ensure();
        try {
            List<java.time.LocalDate> days = gate.jdbc.query(
                    "SELECT held_on FROM class_day WHERE cohort_id = ? ORDER BY held_on",
                    (rs, row) -> rs.getDate("held_on").toLocalDate(),
                    cohortId);
            List<Roll> rolls = new ArrayList<>();
            for (java.time.LocalDate day : days) {
                String sql = """
                        SELECT presence.student, presence.present
                        FROM presence
                        JOIN class_day ON class_day.id = presence.class_day_id
                        WHERE class_day.cohort_id = ? AND class_day.held_on = ?
                        """;
                List<Presence> marks;
                if (actor.role() == Role.ALUNO) {
                    marks = gate.jdbc.query(
                            sql + " AND presence.student = ?",
                            (rs, row) -> new Presence(rs.getString("student"), rs.getBoolean("present")),
                            cohortId,
                            day,
                            actor.username());
                }
                else {
                    marks = gate.jdbc.query(
                            sql + " ORDER BY presence.student",
                            (rs, row) -> new Presence(rs.getString("student"), rs.getBoolean("present")),
                            cohortId,
                            day);
                }
                rolls.add(new Roll(day, marks));
            }
            return rolls;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public UUID announce(UUID cohortId, Actor actor, String title, String body) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            UUID id = UUID.randomUUID();
            gate.jdbc.update(
                    "INSERT INTO announcement (id, cohort_id, title, body) VALUES (?, ?, ?, ?)",
                    id,
                    cohortId,
                    title,
                    body);
            return id;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void withdraw(UUID cohortId, Actor actor, UUID announcementId) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            int removed = gate.jdbc.update(
                    "DELETE FROM announcement WHERE id = ? AND cohort_id = ?",
                    announcementId,
                    cohortId);
            if (removed == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public UUID call(UUID cohortId, Actor actor, java.time.LocalDate heldOn, List<Presence> marks) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            UUID id = UUID.randomUUID();
            gate.jdbc.update("INSERT INTO class_day (id, cohort_id, held_on) VALUES (?, ?, ?)", id, cohortId, heldOn);
            for (Presence mark : marks) {
                gate.jdbc.update(
                        "INSERT INTO presence (class_day_id, student, present) VALUES (?, ?, ?)",
                        id,
                        mark.student(),
                        mark.present());
            }
            return id;
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_DAY);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<Certificate> certificate(UUID cohortId, Actor actor) {
        gate.ensure();
        try {
            if (!gate.enrolled(cohortId, actor.username())) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            List<Certificate> existing = gate.jdbc.query("""
                    SELECT certificate.student, course.name, cohort.name,
                           certificate.average_tenths, certificate.issued_on
                    FROM certificate
                    JOIN cohort ON cohort.id = certificate.cohort_id
                    JOIN course ON course.id = cohort.course_id
                    WHERE certificate.cohort_id = ? AND certificate.student = ?
                    """, (rs, row) -> toCertificate(rs), cohortId, actor.username());
            if (!existing.isEmpty()) {
                return Optional.of(existing.get(0));
            }
            int tenths = averageTenths(cohortId, actor.username());
            if (tenths < 70 || !attendanceAllows(cohortId, actor.username())) {
                throw new Rejection(Failure.NOT_CERTIFIED);
            }
            java.time.LocalDate today = java.time.LocalDate.now();
            gate.jdbc.update(
                    "INSERT INTO certificate (cohort_id, student, average_tenths, issued_on) VALUES (?, ?, ?, ?)",
                    cohortId,
                    actor.username(),
                    tenths,
                    today);
            CohortSummary cohort = gate.query(CohortGate.LIST + " WHERE cohort.id = ?", cohortId).get(0);
            return Optional.of(new Certificate(
                    actor.username(),
                    cohort.courseName(),
                    cohort.name(),
                    formatAverage(tenths),
                    today));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private int averageTenths(UUID cohortId, String student) {
        List<Integer> practices = new ArrayList<>();
        gate.jdbc.query("""
                SELECT submission.grade FROM submission
                JOIN practice ON practice.id = submission.practice_id
                WHERE practice.cohort_id = ? AND submission.student = ? AND submission.grade IS NOT NULL
                """, (rs, row) -> practices.add(rs.getInt("grade")), cohortId, student);
        List<Integer> exams = new ArrayList<>();
        gate.jdbc.query("""
                SELECT attempt.grade FROM attempt
                JOIN exam ON exam.id = attempt.exam_id
                WHERE exam.cohort_id = ? AND attempt.student = ?
                """, (rs, row) -> exams.add(rs.getInt("grade")), cohortId, student);
        int[] weight = gate.weights(cohortId);
        if (weight[0] == 1 && weight[1] == 1) {
            List<Integer> grades = new ArrayList<>();
            grades.addAll(practices);
            grades.addAll(exams);
            return meanTenths(grades);
        }
        if (practices.isEmpty()) {
            return meanTenths(exams);
        }
        if (exams.isEmpty()) {
            return meanTenths(practices);
        }
        double mixed = (mean(practices) * weight[0] + mean(exams) * weight[1]) / (weight[0] + weight[1]);
        return (int) Math.round(mixed * 10);
    }

    private boolean attendanceAllows(UUID cohortId, String student) {
        Integer days = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM class_day WHERE cohort_id = ?",
                Integer.class,
                cohortId);
        if (days == null || days == 0) {
            return true;
        }
        Integer present = gate.jdbc.queryForObject("""
                SELECT COUNT(*) FROM presence
                JOIN class_day ON class_day.id = presence.class_day_id
                WHERE class_day.cohort_id = ? AND presence.student = ? AND presence.present
                """, Integer.class, cohortId, student);
        return present != null && present * 100 >= days * 75;
    }

    private static Certificate toCertificate(ResultSet rs) throws SQLException {
        return new Certificate(
                rs.getString("student"),
                rs.getString(2),
                rs.getString(3),
                formatAverage(rs.getInt("average_tenths")),
                rs.getDate("issued_on").toLocalDate());
    }

    private static String formatAverage(int tenths) {
        return tenths / 10 + "." + Math.floorMod(tenths, 10);
    }

    private static int meanTenths(List<Integer> grades) {
        if (grades.isEmpty()) {
            return 0;
        }
        return (int) Math.round(mean(grades) * 10);
    }

    private static double mean(List<Integer> grades) {
        int sum = 0;
        for (int grade : grades) {
            sum += grade;
        }
        return sum / (double) grades.size();
    }

}
