package skillforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
            if ("aluno".equals(role)) {
                return withOwnProgress(rows, username);
            }
            return withRoster(rows);
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
            if ("aluno".equals(role)) {
                CohortRow mine = withOwnProgress(List.of(row), username).get(0);
                return Optional.of(new CohortPage(mine, course, null, completedLessons(id, username)));
            }
            List<Member> students = membersOf(row);
            return Optional.of(new CohortPage(row, course, students, null));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    void complete(UUID cohortId, UUID lessonId, String student) {
        schema.ensure();
        try {
            if (!enrolled(cohortId, student) || !lessonInCohort(cohortId, lessonId)) {
                throw new CatalogRejection("not-found", HttpStatus.NOT_FOUND);
            }
            jdbc.update("""
                    INSERT INTO completion (cohort_id, lesson_id, student)
                    VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, cohortId, lessonId, student);
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

    private boolean lessonInCohort(UUID cohortId, UUID lessonId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                JOIN cohort ON cohort.course_id = module.course_id
                WHERE lesson.id = ? AND cohort.id = ?
                """, Integer.class, lessonId, cohortId);
        return count != null && count > 0;
    }

    private List<Member> membersOf(CohortRow row) {
        int total = totalLessons(row.courseId());
        Map<String, Integer> done = completedByStudent(row.id());
        List<String> names = jdbc.query(
                "SELECT student FROM enrollment WHERE cohort_id = ? ORDER BY student",
                (rs, index) -> rs.getString("student"),
                row.id());
        List<Member> members = new ArrayList<>();
        for (String name : names) {
            members.add(new Member(name, done.getOrDefault(name, 0), total));
        }
        return members;
    }

    private List<CohortRow> withOwnProgress(List<CohortRow> rows, String username) {
        List<CohortRow> enriched = new ArrayList<>();
        for (CohortRow row : rows) {
            enriched.add(copy(row, null, completedCount(row.id(), username), totalLessons(row.courseId())));
        }
        return enriched;
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
            int total = totalLessons(row.courseId());
            Map<String, Integer> done = completedByStudent(row.id());
            List<Member> members = new ArrayList<>();
            for (String name : roster.get(row.id())) {
                members.add(new Member(name, done.getOrDefault(name, 0), total));
            }
            withStudents.add(copy(row, List.copyOf(members), null, null));
        }
        return withStudents;
    }

    private Set<UUID> completedLessons(UUID cohortId, String student) {
        List<UUID> ids = jdbc.query(
                "SELECT lesson_id FROM completion WHERE cohort_id = ? AND student = ?",
                (rs, row) -> rs.getObject("lesson_id", UUID.class),
                cohortId,
                student);
        return new LinkedHashSet<>(ids);
    }

    private int totalLessons(UUID courseId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                WHERE module.course_id = ?
                """, Integer.class, courseId);
        return count == null ? 0 : count;
    }

    private int completedCount(UUID cohortId, String student) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM completion
                JOIN lesson ON lesson.id = completion.lesson_id
                JOIN module ON module.id = lesson.module_id
                JOIN cohort ON cohort.id = completion.cohort_id AND cohort.course_id = module.course_id
                WHERE completion.cohort_id = ? AND completion.student = ?
                """, Integer.class, cohortId, student);
        return count == null ? 0 : count;
    }

    private Map<String, Integer> completedByStudent(UUID cohortId) {
        Map<String, Integer> counts = new HashMap<>();
        jdbc.query("""
                SELECT completion.student, COUNT(*) AS done
                FROM completion
                JOIN lesson ON lesson.id = completion.lesson_id
                JOIN module ON module.id = lesson.module_id
                JOIN cohort ON cohort.id = completion.cohort_id AND cohort.course_id = module.course_id
                WHERE completion.cohort_id = ?
                GROUP BY completion.student
                """, (rs, row) -> {
                    counts.put(rs.getString("student"), rs.getInt("done"));
                    return null;
                }, cohortId);
        return counts;
    }

    private static CohortRow copy(CohortRow row, List<Member> students, Integer completed, Integer total) {
        return new CohortRow(
                row.id(),
                row.name(),
                row.courseId(),
                row.courseName(),
                row.teacher(),
                students,
                completed,
                total);
    }

    private List<CohortRow> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> new CohortRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_name"),
                rs.getString("teacher"),
                null,
                null,
                null), args);
    }

    record Member(String student, int completed, int total) {
    }

    record CohortRow(
            UUID id,
            String name,
            UUID courseId,
            String courseName,
            String teacher,
            List<Member> students,
            Integer completed,
            Integer total) {
    }

    record CohortPage(
            CohortRow cohort,
            CatalogStore.CourseView course,
            List<Member> students,
            Set<UUID> done) {
    }

}
