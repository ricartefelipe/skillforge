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
import org.springframework.stereotype.Component;

import skillforge.domain.catalog.Course;
import skillforge.domain.cohort.CohortDetail;
import skillforge.domain.cohort.CohortListing;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.cohort.CohortSummary;
import skillforge.domain.cohort.ExamRepository;
import skillforge.domain.cohort.GradeAverage;
import skillforge.domain.cohort.PracticeRepository;
import skillforge.domain.cohort.Progress;
import skillforge.domain.cohort.Report;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

@Component
class JdbcCohortRepository implements CohortRepository {

    private final CohortGate gate;

    private final PracticeRepository practices;

    private final ExamRepository exams;

    JdbcCohortRepository(CohortGate gate, PracticeRepository practices, ExamRepository exams) {
        this.gate = gate;
        this.practices = practices;
        this.exams = exams;
    }

    @Override
    public UUID open(UUID courseId, String name, String teacher) {
        gate.ensure();
        if (courseId == null || gate.courses.find(courseId).isEmpty()) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        try {
            gate.jdbc.update(
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
        gate.ensure();
        if (!gate.exists(cohortId)) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        try {
            gate.jdbc.update("INSERT INTO enrollment (cohort_id, student) VALUES (?, ?)", cohortId, student);
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
        gate.ensure();
        try {
            List<CohortSummary> rows = switch (actor.role()) {
                case ADMINISTRACAO -> gate.query(CohortGate.LIST + " ORDER BY cohort.name");
                case DOCENTE -> gate.query(
                        CohortGate.LIST + " WHERE cohort.teacher = ? ORDER BY cohort.name",
                        actor.username());
                case ALUNO -> gate.query(CohortGate.LIST + """
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
        gate.ensure();
        try {
            List<CohortSummary> rows = gate.query(CohortGate.LIST + " WHERE cohort.id = ?", id);
            if (rows.isEmpty() || !gate.allowed(rows.get(0), actor)) {
                return Optional.empty();
            }
            CohortSummary row = rows.get(0);
            Course course = gate.courses.find(row.courseId()).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
            return switch (actor.role()) {
                case ALUNO -> {
                    CohortListing.Student mine = withOwnProgress(List.of(row), actor.username()).get(0);
                    yield Optional.of(new CohortDetail.Student(
                            mine.summary(),
                            mine.completed(),
                            mine.total(),
                            course,
                            completedLessons(id, actor.username()),
                            practices.inCohort(id, actor),
                            exams.inCohort(id, actor)));
                }
                case DOCENTE, ADMINISTRACAO -> Optional.of(new CohortDetail.Staff(
                        row,
                        membersOf(row),
                        course,
                        practices.inCohort(id, actor),
                        exams.inCohort(id, actor)));
            };
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public boolean teaches(String teacher, UUID courseId) {
        gate.ensure();
        try {
            Integer count = gate.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cohort WHERE course_id = ? AND teacher = ?",
                    Integer.class,
                    courseId,
                    teacher);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Set<UUID> courseIds(Actor actor) {
        gate.ensure();
        try {
            String sql = switch (actor.role()) {
                case ADMINISTRACAO -> "SELECT id FROM course";
                case DOCENTE -> "SELECT DISTINCT course_id FROM cohort WHERE teacher = ?";
                case ALUNO -> """
                        SELECT DISTINCT cohort.course_id
                        FROM cohort
                        JOIN enrollment ON enrollment.cohort_id = cohort.id
                        WHERE enrollment.student = ?
                        """;
            };
            if (actor.role() == Role.ADMINISTRACAO) {
                return new LinkedHashSet<>(gate.jdbc.query(sql, (rs, row) -> rs.getObject("id", UUID.class)));
            }
            return new LinkedHashSet<>(
                    gate.jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), actor.username()));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<Report> report(Actor actor) {
        gate.ensure();
        try {
            List<Report> reports = new ArrayList<>();
            for (CohortListing listing : visible(actor)) {
                UUID cohortId = listing.summary().id();
                int[] weight = gate.weights(cohortId);
                List<skillforge.domain.cohort.Practice> cohortPractices = practices.inCohort(cohortId, actor);
                List<skillforge.domain.cohort.Exam> cohortExams = exams.inCohort(cohortId, actor);
                java.util.OptionalInt tenths = GradeAverage.tenths(
                        practiceGrades(cohortPractices),
                        examGrades(cohortExams),
                        weight[0],
                        weight[1]);
                int days = actor.role() == Role.ALUNO ? counted(
                        "SELECT COUNT(*) FROM class_day WHERE cohort_id = ?",
                        cohortId) : 0;
                int present = actor.role() == Role.ALUNO ? counted("""
                        SELECT COUNT(*) FROM presence
                        JOIN class_day ON class_day.id = presence.class_day_id
                        WHERE class_day.cohort_id = ? AND presence.student = ? AND presence.present
                        """, cohortId, actor.username()) : 0;
                reports.add(new Report(
                        cohortId,
                        listing.summary().name(),
                        listing.summary().courseName(),
                        cohortPractices,
                        cohortExams,
                        weight[0],
                        weight[1],
                        tenths.isPresent() ? GradeAverage.format(tenths.getAsInt()) : null,
                        tenths.isPresent() && actor.role() == Role.ALUNO
                                && GradeAverage.earnsCertificate(tenths.getAsInt(), days, present)));
            }
            return List.copyOf(reports);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void complete(UUID cohortId, UUID lessonId, String student) {
        gate.ensure();
        try {
            if (!gate.enrolled(cohortId, student) || !gate.lessonInCohort(cohortId, lessonId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            gate.jdbc.update("""
                    INSERT INTO completion (cohort_id, lesson_id, student)
                    VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, cohortId, lessonId, student);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private List<Progress> membersOf(CohortSummary row) {
        int total = totalLessons(row.courseId());
        Map<String, Integer> done = completedByStudent(row.id());
        List<String> names = gate.jdbc.query(
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
        gate.jdbc.query("SELECT cohort_id, student FROM enrollment ORDER BY student", (rs, row) -> {
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
        List<UUID> ids = gate.jdbc.query(
                "SELECT lesson_id FROM completion WHERE cohort_id = ? AND student = ?",
                (rs, row) -> rs.getObject("lesson_id", UUID.class),
                cohortId,
                student);
        return new LinkedHashSet<>(ids);
    }

    private int totalLessons(UUID courseId) {
        Integer count = gate.jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                WHERE module.course_id = ?
                """, Integer.class, courseId);
        return count == null ? 0 : count;
    }

    private int completedCount(UUID cohortId, String student) {
        Integer count = gate.jdbc.queryForObject("""
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
        gate.jdbc.query("""
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

    private int counted(String sql, Object... args) {
        Integer count = gate.jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private static List<Integer> practiceGrades(List<skillforge.domain.cohort.Practice> cohortPractices) {
        List<Integer> grades = new ArrayList<>();
        for (skillforge.domain.cohort.Practice practice : cohortPractices) {
            for (skillforge.domain.cohort.Delivery delivery : practice.deliveries()) {
                if (delivery.grade() != null) {
                    grades.add(delivery.grade());
                }
            }
        }
        return grades;
    }

    private static List<Integer> examGrades(List<skillforge.domain.cohort.Exam> cohortExams) {
        List<Integer> grades = new ArrayList<>();
        for (skillforge.domain.cohort.Exam exam : cohortExams) {
            for (skillforge.domain.cohort.ExamScore score : exam.scores()) {
                grades.add(score.grade());
            }
        }
        return grades;
    }

}
