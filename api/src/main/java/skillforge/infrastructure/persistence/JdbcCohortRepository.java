package skillforge.infrastructure.persistence;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.cohort.CohortAccess;
import skillforge.domain.cohort.CohortDetail;
import skillforge.domain.cohort.CohortListing;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.cohort.CohortSummary;
import skillforge.domain.cohort.Progress;
import skillforge.domain.identity.Actor;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

@Component
class JdbcCohortRepository implements CohortRepository {

    private static final String LIST = """
            SELECT cohort.id, cohort.name, cohort.teacher, course.id AS course_id, course.name AS course_name
            FROM cohort
            JOIN course ON course.id = cohort.course_id
            """;

    private final Schema schema;

    private final JdbcTemplate jdbc;

    private final CourseRepository courses;

    JdbcCohortRepository(Schema schema, JdbcTemplate jdbc, CourseRepository courses) {
        this.schema = schema;
        this.jdbc = jdbc;
        this.courses = courses;
    }

    @Override
    public UUID open(UUID courseId, String name, String teacher) {
        schema.ensure();
        if (courseId == null || courses.find(courseId).isEmpty()) {
            throw new Rejection(Failure.NOT_FOUND);
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
            throw new Rejection(Failure.DUPLICATE_COHORT);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
        return id;
    }

    @Override
    public void enroll(UUID cohortId, String student) {
        schema.ensure();
        if (!exists(cohortId)) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        try {
            jdbc.update("INSERT INTO enrollment (cohort_id, student) VALUES (?, ?)", cohortId, student);
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.ALREADY_ENROLLED);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<CohortListing> visible(Actor actor) {
        schema.ensure();
        try {
            List<CohortSummary> rows = switch (actor.role()) {
                case ADMINISTRACAO -> query(LIST + " ORDER BY cohort.name");
                case DOCENTE -> query(LIST + " WHERE cohort.teacher = ? ORDER BY cohort.name", actor.username());
                case ALUNO -> query(LIST + """
                         WHERE EXISTS (
                             SELECT 1 FROM enrollment
                             WHERE enrollment.cohort_id = cohort.id AND enrollment.student = ?
                         )
                         ORDER BY cohort.name
                        """, actor.username());
            };
            return switch (actor.role()) {
                case ALUNO -> {
                    List<CohortListing> own = new ArrayList<>();
                    own.addAll(withOwnProgress(rows, actor.username()));
                    yield List.copyOf(own);
                }
                case DOCENTE, ADMINISTRACAO -> withRoster(rows);
            };
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<CohortDetail> page(UUID id, Actor actor) {
        schema.ensure();
        try {
            List<CohortSummary> rows = query(LIST + " WHERE cohort.id = ?", id);
            if (rows.isEmpty() || !allowed(rows.get(0), actor)) {
                return Optional.empty();
            }
            CohortSummary row = rows.get(0);
            Course course = courses.find(row.courseId()).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
            return switch (actor.role()) {
                case ALUNO -> {
                    CohortListing.Student mine = withOwnProgress(List.of(row), actor.username()).get(0);
                    yield Optional.of(new CohortDetail.Student(
                            mine.summary(),
                            mine.completed(),
                            mine.total(),
                            course,
                            completedLessons(id, actor.username())));
                }
                case DOCENTE, ADMINISTRACAO -> Optional.of(new CohortDetail.Staff(row, membersOf(row), course));
            };
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void complete(UUID cohortId, UUID lessonId, String student) {
        schema.ensure();
        try {
            if (!enrolled(cohortId, student) || !lessonInCohort(cohortId, lessonId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            jdbc.update("""
                    INSERT INTO completion (cohort_id, lesson_id, student)
                    VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, cohortId, lessonId, student);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private boolean allowed(CohortSummary row, Actor actor) {
        return CohortAccess.canSee(actor.role(), row.teacher(), actor.username(), enrolled(row.id(), actor.username()));
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
            throw new Unavailable(ex);
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

    private List<Progress> membersOf(CohortSummary row) {
        int total = totalLessons(row.courseId());
        Map<String, Integer> done = completedByStudent(row.id());
        List<String> names = jdbc.query(
                "SELECT student FROM enrollment WHERE cohort_id = ? ORDER BY student",
                (rs, index) -> rs.getString("student"),
                row.id());
        List<Progress> members = new ArrayList<>();
        for (String name : names) {
            members.add(new Progress(name, done.getOrDefault(name, 0), total));
        }
        return members;
    }

    private List<CohortListing.Student> withOwnProgress(List<CohortSummary> rows, String username) {
        List<CohortListing.Student> enriched = new ArrayList<>();
        for (CohortSummary row : rows) {
            enriched.add(new CohortListing.Student(
                    row,
                    completedCount(row.id(), username),
                    totalLessons(row.courseId())));
        }
        return enriched;
    }

    private List<CohortListing> withRoster(List<CohortSummary> rows) {
        Map<UUID, List<String>> roster = new LinkedHashMap<>();
        for (CohortSummary row : rows) {
            roster.put(row.id(), new ArrayList<>());
        }
        if (roster.isEmpty()) {
            return List.of();
        }
        jdbc.query("SELECT cohort_id, student FROM enrollment ORDER BY student", (rs, row) -> {
            List<String> students = roster.get(rs.getObject("cohort_id", UUID.class));
            if (students != null) {
                students.add(rs.getString("student"));
            }
            return null;
        });
        List<CohortListing> withStudents = new ArrayList<>();
        for (CohortSummary row : rows) {
            int total = totalLessons(row.courseId());
            Map<String, Integer> done = completedByStudent(row.id());
            List<Progress> members = new ArrayList<>();
            for (String name : roster.get(row.id())) {
                members.add(new Progress(name, done.getOrDefault(name, 0), total));
            }
            withStudents.add(new CohortListing.Staff(row, members));
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

    private List<CohortSummary> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> new CohortSummary(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("course_id", UUID.class),
                rs.getString("course_name"),
                rs.getString("teacher")), args);
    }

}
