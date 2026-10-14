package skillforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class CohortStore {

    private static final String LIST = """
            SELECT cohort.id, cohort.name, cohort.teacher, course.id AS course_id, course.name AS course_name
            FROM cohort
            JOIN course ON course.id = cohort.course_id
            """;

    private final Schema schema;

    private final JdbcTemplate jdbc;

    private final CatalogStore catalog;

    CohortStore(Schema schema, JdbcTemplate jdbc, CatalogStore catalog) {
        this.schema = schema;
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    UUID open(UUID courseId, String name, String teacher) {
        schema.ensure();
        if (courseId == null || catalog.course(courseId).isEmpty()) {
            throw new CatalogRejection("not-found", HttpStatus.NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO cohort (id, course_id, name, teacher) VALUES (?, ?, ?, ?)",
                    id,
                    courseId,
                    name,
                    teacher);
        }
        catch (DuplicateKeyException ex) {
            throw new CatalogRejection("duplicate-cohort", HttpStatus.CONFLICT);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
        return id;
    }

    void enroll(UUID cohortId, String student) {
        schema.ensure();
        if (!exists(cohortId)) {
            throw new CatalogRejection("not-found", HttpStatus.NOT_FOUND);
        }
        try {
            jdbc.update("INSERT INTO enrollment (cohort_id, student) VALUES (?, ?)", cohortId, student);
        }
        catch (DuplicateKeyException ex) {
            throw new CatalogRejection("already-enrolled", HttpStatus.CONFLICT);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    List<CohortRow> visible(String role, String username) {
        schema.ensure();
        try {
            List<CohortRow> rows = switch (role) {
                case "administracao" -> query(LIST + " ORDER BY cohort.name");
                case "docente" -> query(LIST + " WHERE cohort.teacher = ? ORDER BY cohort.name", username);
                case "aluno" -> query(LIST + """
                         WHERE EXISTS (
                             SELECT 1 FROM enrollment
                             WHERE enrollment.cohort_id = cohort.id AND enrollment.student = ?
                         )
                         ORDER BY cohort.name
                        """, username);
                default -> List.of();
            };
            if (!"aluno".equals(role)) {
                return withRoster(rows);
            }
            return rows;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    Optional<CohortPage> page(UUID id, String role, String username) {
        schema.ensure();
        try {
            List<CohortRow> rows = query(LIST + " WHERE cohort.id = ?", id);
            if (rows.isEmpty() || !allowed(rows.get(0), role, username)) {
                return Optional.empty();
            }
            CohortRow row = rows.get(0);
            CatalogStore.CourseView course = catalog.course(row.courseId())
                    .orElseThrow(() -> new CatalogRejection("not-found", HttpStatus.NOT_FOUND));
            List<String> students = "aluno".equals(role) ? null : studentsOf(id);
            return Optional.of(new CohortPage(row, course, students));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    private boolean allowed(CohortRow row, String role, String username) {
        return switch (role) {
            case "administracao" -> true;
            case "docente" -> row.teacher().equals(username);
            case "aluno" -> enrolled(row.id(), username);
            default -> false;
        };
    }

    private boolean enrolled(UUID cohortId, String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE cohort_id = ? AND student = ?",
                Integer.class,
                cohortId,
                username);
        return count != null && count > 0;
    }

    private boolean exists(UUID id) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cohort WHERE id = ?", Integer.class, id);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    private List<String> studentsOf(UUID cohortId) {
        return jdbc.query(
                "SELECT student FROM enrollment WHERE cohort_id = ? ORDER BY student",
                (rs, row) -> rs.getString("student"),
                cohortId);
    }

    private List<CohortRow> withRoster(List<CohortRow> rows) {
        Map<UUID, List<String>> roster = new LinkedHashMap<>();
        for (CohortRow row : rows) {
            roster.put(row.id(), new ArrayList<>());
        }
        if (roster.isEmpty()) {
            return rows;
        }
        jdbc.query("SELECT cohort_id, student FROM enrollment ORDER BY student", (rs, row) -> {
            List<String> students = roster.get(rs.getObject("cohort_id", UUID.class));
            if (students != null) {
                students.add(rs.getString("student"));
            }
            return null;
        });
        List<CohortRow> withStudents = new ArrayList<>();
        for (CohortRow row : rows) {
            withStudents.add(new CohortRow(
                    row.id(),
                    row.name(),
                    row.courseId(),
                    row.courseName(),
                    row.teacher(),
                    List.copyOf(roster.get(row.id()))));
        }
        return withStudents;
    }

    private List<CohortRow> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> new CohortRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_name"),
                rs.getString("teacher"),
                null), args);
    }

    record CohortRow(
            UUID id,
            String name,
            UUID courseId,
            String courseName,
            String teacher,
            List<String> students) {
    }

    record CohortPage(CohortRow cohort, CatalogStore.CourseView course, List<String> students) {
    }

}
