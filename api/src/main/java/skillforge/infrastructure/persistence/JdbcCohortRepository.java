package skillforge.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.transaction.annotation.Transactional;

import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.cohort.Choice;
import skillforge.domain.cohort.CohortAccess;
import skillforge.domain.cohort.Exam;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.cohort.ExamGrade;
import skillforge.domain.cohort.ExamScore;
import skillforge.domain.cohort.CohortDetail;
import skillforge.domain.cohort.CohortListing;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.cohort.CohortSummary;
import skillforge.domain.cohort.Delivery;
import skillforge.domain.cohort.Practice;
import skillforge.domain.cohort.Progress;
import skillforge.domain.cohort.Question;
import skillforge.domain.cohort.Report;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
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
                            completedLessons(id, actor.username()),
                            practicesOf(id, actor),
                            examsOf(id, actor)));
                }
                case DOCENTE, ADMINISTRACAO -> Optional.of(new CohortDetail.Staff(
                        row,
                        membersOf(row),
                        course,
                        practicesOf(id, actor),
                        examsOf(id, actor)));
            };
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public UUID openPractice(UUID cohortId, Actor actor, String title, String prompt) {
        schema.ensure();
        UUID id = UUID.randomUUID();
        try {
            CohortSummary cohort = requireLead(cohortId, actor);
            jdbc.update(
                    "INSERT INTO practice (id, cohort_id, title, prompt) VALUES (?, ?, ?, ?)",
                    id,
                    cohort.id(),
                    title,
                    prompt);
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
        schema.ensure();
        try {
            if (!enrolled(cohortId, actor.username()) || !practiceInCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            String feedback = currentFeedback(practiceId, actor.username());
            if (feedback == null && !submissionExists(practiceId, actor.username())) {
                jdbc.update(
                        "INSERT INTO submission (practice_id, student, body) VALUES (?, ?, ?)",
                        practiceId,
                        actor.username(),
                        text);
                return;
            }
            if (feedback != null) {
                throw new Rejection(Failure.ALREADY_RETURNED);
            }
            jdbc.update(
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
    public void feedback(UUID cohortId, UUID practiceId, Actor actor, String student, String text, int grade) {
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            if (!practiceInCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            int updated = jdbc.update(
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
    public boolean teaches(String teacher, UUID courseId) {
        schema.ensure();
        try {
            Integer count = jdbc.queryForObject(
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
    public java.util.Set<UUID> courseIds(Actor actor) {
        schema.ensure();
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
                return new java.util.LinkedHashSet<>(jdbc.query(sql, (rs, row) -> rs.getObject("id", UUID.class)));
            }
            return new java.util.LinkedHashSet<>(
                    jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), actor.username()));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<Report> report(Actor actor) {
        schema.ensure();
        try {
            List<Report> reports = new java.util.ArrayList<>();
            for (CohortListing listing : visible(actor)) {
                reports.add(new Report(
                        listing.summary().id(),
                        listing.summary().name(),
                        listing.summary().courseName(),
                        practicesOf(listing.summary().id(), actor),
                        examsOf(listing.summary().id(), actor)));
            }
            return List.copyOf(reports);
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

    @Override
    @Transactional
    public UUID openExam(UUID cohortId, Actor actor, ExamDraft draft) {
        schema.ensure();
        try {
            CohortSummary cohort = requireLead(cohortId, actor);
            UUID examId = UUID.randomUUID();
            jdbc.update("INSERT INTO exam (id, cohort_id, title) VALUES (?, ?, ?)", examId, cohort.id(), draft.title());
            int position = 1;
            for (ExamDraft.QuestionDraft question : draft.questions()) {
                UUID questionId = UUID.randomUUID();
                jdbc.update(
                        "INSERT INTO question (id, exam_id, position, prompt) VALUES (?, ?, ?, ?)",
                        questionId,
                        examId,
                        position,
                        question.prompt());
                position++;
                for (ExamDraft.ChoiceDraft choice : question.choices()) {
                    jdbc.update(
                            "INSERT INTO choice (id, question_id, label, correct) VALUES (?, ?, ?, ?)",
                            UUID.randomUUID(),
                            questionId,
                            choice.label(),
                            choice.correct());
                }
            }
            return examId;
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_EXAM);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    @Transactional
    public void takeExam(UUID cohortId, UUID examId, Actor actor, Map<UUID, UUID> answers) {
        schema.ensure();
        try {
            if (!enrolled(cohortId, actor.username()) || !examInCohort(cohortId, examId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Exam exam = examsOf(cohortId, actor).stream()
                    .filter(item -> item.id().equals(examId))
                    .findFirst()
                    .orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
            if (!exam.scores().isEmpty()) {
                throw new Rejection(Failure.ALREADY_TAKEN);
            }
            int grade = ExamGrade.of(exam, answers);
            jdbc.update(
                    "INSERT INTO attempt (exam_id, student, grade) VALUES (?, ?, ?)",
                    examId,
                    actor.username(),
                    grade);
            for (Map.Entry<UUID, UUID> answer : answers.entrySet()) {
                jdbc.update(
                        "INSERT INTO attempt_answer (exam_id, student, question_id, choice_id) VALUES (?, ?, ?, ?)",
                        examId,
                        actor.username(),
                        answer.getKey(),
                        answer.getValue());
            }
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.ALREADY_TAKEN);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private boolean examInCohort(UUID cohortId, UUID examId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE id = ? AND cohort_id = ?",
                Integer.class,
                examId,
                cohortId);
        return count != null && count > 0;
    }

    private List<Exam> examsOf(UUID cohortId, Actor actor) {
        List<IdTitle> exams = jdbc.query(
                "SELECT id, title FROM exam WHERE cohort_id = ? ORDER BY title",
                (rs, row) -> new IdTitle(rs.getObject("id", UUID.class), rs.getString("title")),
                cohortId);
        if (exams.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<QuestionRow>> questions = new LinkedHashMap<>();
        for (IdTitle exam : exams) {
            questions.put(exam.id(), new ArrayList<>());
        }
        jdbc.query("""
                SELECT id, exam_id, prompt
                FROM question
                WHERE exam_id IN (SELECT id FROM exam WHERE cohort_id = ?)
                ORDER BY position
                """, (rs, row) -> {
                    questions.get(rs.getObject("exam_id", UUID.class)).add(new QuestionRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("prompt")));
                    return null;
                }, cohortId);
        Map<UUID, List<Choice>> choices = new LinkedHashMap<>();
        jdbc.query("""
                SELECT choice.id, choice.question_id, choice.label, choice.correct
                FROM choice
                JOIN question ON question.id = choice.question_id
                JOIN exam ON exam.id = question.exam_id
                WHERE exam.cohort_id = ?
                ORDER BY choice.label
                """, (rs, row) -> {
                    choices.computeIfAbsent(rs.getObject("question_id", UUID.class), id -> new ArrayList<>())
                            .add(new Choice(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("label"),
                                    rs.getBoolean("correct")));
                    return null;
                }, cohortId);
        Map<UUID, List<ExamScore>> scores = new LinkedHashMap<>();
        for (IdTitle exam : exams) {
            scores.put(exam.id(), new ArrayList<>());
        }
        if (actor.role() == Role.ALUNO) {
            jdbc.query("""
                    SELECT exam_id, student, grade FROM attempt
                    WHERE student = ? AND exam_id IN (SELECT id FROM exam WHERE cohort_id = ?)
                    """, (rs, row) -> keepScore(scores, rs), actor.username(), cohortId);
        }
        else {
            jdbc.query("""
                    SELECT exam_id, student, grade FROM attempt
                    WHERE exam_id IN (SELECT id FROM exam WHERE cohort_id = ?)
                    ORDER BY student
                    """, (rs, row) -> keepScore(scores, rs), cohortId);
        }
        List<Exam> assembled = new ArrayList<>();
        for (IdTitle exam : exams) {
            List<Question> examQuestions = new ArrayList<>();
            for (QuestionRow question : questions.get(exam.id())) {
                examQuestions.add(new Question(
                        question.id(),
                        question.prompt(),
                        choices.getOrDefault(question.id(), List.of())));
            }
            assembled.add(new Exam(exam.id(), exam.title(), examQuestions, scores.get(exam.id())));
        }
        return List.copyOf(assembled);
    }

    private static Object keepScore(Map<UUID, List<ExamScore>> scores, ResultSet rs) throws SQLException {
        List<ExamScore> found = scores.get(rs.getObject("exam_id", UUID.class));
        if (found != null) {
            found.add(new ExamScore(rs.getString("student"), rs.getInt("grade")));
        }
        return null;
    }

    private CohortSummary requireLead(UUID cohortId, Actor actor) {
        List<CohortSummary> rows = query(LIST + " WHERE cohort.id = ?", cohortId);
        if (rows.isEmpty() || !CohortAccess.canLead(actor.role(), rows.get(0).teacher(), actor.username())) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return rows.get(0);
    }

    private boolean practiceInCohort(UUID cohortId, UUID practiceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice WHERE id = ? AND cohort_id = ?",
                Integer.class,
                practiceId,
                cohortId);
        return count != null && count > 0;
    }

    private boolean submissionExists(UUID practiceId, String student) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM submission WHERE practice_id = ? AND student = ?",
                Integer.class,
                practiceId,
                student);
        return count != null && count > 0;
    }

    private String currentFeedback(UUID practiceId, String student) {
        List<String> rows = jdbc.query(
                "SELECT feedback FROM submission WHERE practice_id = ? AND student = ?",
                (rs, row) -> rs.getString("feedback"),
                practiceId,
                student);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private List<Practice> practicesOf(UUID cohortId, Actor actor) {
        List<PracticeSeed> seeds = jdbc.query(
                "SELECT id, title, prompt FROM practice WHERE cohort_id = ? ORDER BY title",
                (rs, row) -> new PracticeSeed(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("prompt")),
                cohortId);
        Map<UUID, List<Delivery>> deliveries = new LinkedHashMap<>();
        for (PracticeSeed seed : seeds) {
            deliveries.put(seed.id(), new ArrayList<>());
        }
        if (seeds.isEmpty()) {
            return List.of();
        }
        if (actor.role() == Role.ALUNO) {
            loadDeliveries(deliveries, cohortId, actor.username());
        }
        else {
            loadDeliveries(deliveries, cohortId, null);
        }
        List<Practice> practices = new ArrayList<>();
        for (PracticeSeed seed : seeds) {
            practices.add(new Practice(seed.id(), seed.title(), seed.prompt(), deliveries.get(seed.id())));
        }
        return List.copyOf(practices);
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
            jdbc.query(sql + " ORDER BY submission.student", (rs, row) -> keep(deliveries, rs), cohortId);
            return;
        }
        jdbc.query(
                sql + " AND submission.student = ? ORDER BY submission.student",
                (rs, row) -> keep(deliveries, rs),
                cohortId,
                student);
    }

    private static Object keep(Map<UUID, List<Delivery>> deliveries, ResultSet rs) throws SQLException {
        UUID practiceId = rs.getObject("practice_id", UUID.class);
        List<Delivery> found = deliveries.get(practiceId);
        if (found != null) {
            Integer grade = rs.getObject("grade", Integer.class);
            found.add(new Delivery(rs.getString("student"), rs.getString("body"), rs.getString("feedback"), grade));
        }
        return null;
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

    private record PracticeSeed(UUID id, String title, String prompt) {
    }

    private record IdTitle(UUID id, String title) {
    }

    private record QuestionRow(UUID id, String prompt) {
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
