package skillforge.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.Lesson;
import skillforge.domain.catalog.LessonAsset;
import skillforge.domain.catalog.LessonContent;
import skillforge.domain.catalog.LessonFile;
import skillforge.domain.catalog.LessonKind;
import skillforge.domain.catalog.ModuleOrder;
import skillforge.domain.catalog.ScormZip;
import skillforge.domain.catalog.LessonText;
import skillforge.domain.catalog.Module;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

@Component
class JdbcCourseRepository implements CourseRepository {

    private final Schema schema;

    private final JdbcTemplate jdbc;

    JdbcCourseRepository(Schema schema, JdbcTemplate jdbc) {
        this.schema = schema;
        this.jdbc = jdbc;
    }

    @Override
    public UUID addCourse(String name) {
        schema.ensure();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO course (id, name) VALUES (?, ?)", id, name);
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_COURSE);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
        return id;
    }

    @Override
    public UUID addModule(UUID courseId, String name) {
        schema.ensure();
        if (!courseExists(courseId)) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO module (id, course_id, name, position)
                    SELECT ?, ?, ?, COALESCE(MAX(position), 0) + 1 FROM module WHERE course_id = ?
                    """, id, courseId, name, courseId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
        return id;
    }

    @Override
    public UUID addLesson(UUID moduleId, String title, LessonContent.Accepted content) {
        schema.ensure();
        if (!moduleExists(moduleId)) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO lesson (id, module_id, title, position, kind, markdown, media, media_type)
                    SELECT ?, ?, ?, COALESCE(MAX(position), 0) + 1, ?, ?, ?, ? FROM lesson WHERE module_id = ?
                    """, id, moduleId, title, content.kind().wire(), content.markdown(), content.media(),
                    content.mediaType(), moduleId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
        return id;
    }

    @Override
    public void removeLesson(UUID lessonId) {
        schema.ensure();
        try {
            jdbc.update(
                    "DELETE FROM reply WHERE doubt_id IN (SELECT id FROM doubt WHERE lesson_id = ?)",
                    lessonId);
            jdbc.update("DELETE FROM doubt WHERE lesson_id = ?", lessonId);
            jdbc.update("DELETE FROM completion WHERE lesson_id = ?", lessonId);
            jdbc.update("DELETE FROM lesson_asset WHERE lesson_id = ?", lessonId);
            jdbc.update("DELETE FROM scorm_track WHERE lesson_id = ?", lessonId);
            jdbc.update("DELETE FROM scorm_file WHERE lesson_id = ?", lessonId);
            jdbc.update("DELETE FROM lesson_tool WHERE lesson_id = ?", lessonId);
            int removed = jdbc.update("DELETE FROM lesson WHERE id = ?", lessonId);
            if (removed == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<UUID> courseOfLesson(UUID lessonId) {
        schema.ensure();
        try {
            List<UUID> ids = jdbc.query("""
                    SELECT module.course_id
                    FROM lesson
                    JOIN module ON module.id = lesson.module_id
                    WHERE lesson.id = ?
                    """, (rs, row) -> rs.getObject(1, UUID.class), lessonId);
            return ids.stream().findFirst();
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<Course> list() {
        schema.ensure();
        try {
            List<CourseSeed> courses = jdbc.query(
                    "SELECT id, name FROM course ORDER BY name",
                    (rs, row) -> new CourseSeed(rs.getObject("id", UUID.class), rs.getString("name")));
            List<ModuleSeed> modules = jdbc.query(
                    "SELECT id, course_id, name FROM module ORDER BY position, name",
                    (rs, row) -> new ModuleSeed(
                            rs.getObject("id", UUID.class),
                            rs.getObject("course_id", UUID.class),
                            rs.getString("name")));
            Map<UUID, List<Lesson>> lessonsByModule = new LinkedHashMap<>();
            jdbc.query(
                    "SELECT id, module_id, title, kind FROM lesson ORDER BY position, title",
                    (rs, row) -> {
                        UUID moduleId = rs.getObject("module_id", UUID.class);
                        lessonsByModule.computeIfAbsent(moduleId, id -> new ArrayList<>())
                                .add(new Lesson(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("title"),
                                        kind(rs.getString("kind"))));
                        return null;
                    });
            Map<UUID, List<Module>> modulesByCourse = new LinkedHashMap<>();
            for (ModuleSeed module : modules) {
                modulesByCourse.computeIfAbsent(module.courseId(), id -> new ArrayList<>())
                        .add(new Module(
                                module.id(),
                                module.name(),
                                lessonsByModule.getOrDefault(module.id(), List.of())));
            }
            List<Course> assembled = new ArrayList<>();
            for (CourseSeed course : courses) {
                assembled.add(new Course(
                        course.id(),
                        course.name(),
                        modulesByCourse.getOrDefault(course.id(), List.of())));
            }
            return List.copyOf(assembled);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<Course> find(UUID id) {
        for (Course course : list()) {
            if (course.id().equals(id)) {
                return Optional.of(course);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<LessonText> lesson(UUID id) {
        schema.ensure();
        try {
            List<LessonText> rows = jdbc.query(
                    "SELECT id, title, kind, markdown FROM lesson WHERE id = ?",
                    (rs, row) -> new LessonText(
                            rs.getObject("id", UUID.class),
                            rs.getString("title"),
                            kind(rs.getString("kind")),
                            rs.getString("markdown")),
                    id);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(rows.get(0));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<UUID> courseOfModule(UUID moduleId) {
        schema.ensure();
        try {
            List<UUID> rows = jdbc.query(
                    "SELECT course_id FROM module WHERE id = ?",
                    (rs, row) -> rs.getObject("course_id", UUID.class),
                    moduleId);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(rows.get(0));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<LessonFile> media(UUID id) {
        schema.ensure();
        try {
            List<StoredFile> rows = jdbc.query(
                    "SELECT kind, media, media_type FROM lesson WHERE id = ?",
                    (rs, row) -> new StoredFile(rs.getString("kind"), rs.getBytes("media"), rs.getString("media_type")),
                    id);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            StoredFile stored = rows.get(0);
            if (stored.kind().equals(LessonKind.MARKDOWN.wire()) || stored.bytes() == null) {
                return Optional.empty();
            }
            return Optional.of(new LessonFile(kind(stored.kind()), stored.bytes(), stored.mediaType()));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    @Transactional
    public UUID addScorm(UUID moduleId, String title, byte[] zip) {
        schema.ensure();
        ScormZip.Unpacked unpacked = ScormZip.read(zip);
        UUID id = addLesson(moduleId, title, new LessonContent.Accepted(
                LessonKind.SCORM, null, zip, "application/zip"));
        for (ScormZip.Entry file : unpacked.files()) {
            jdbc.update(
                    "INSERT INTO scorm_file (lesson_id, path, media, media_type) VALUES (?, ?, ?, ?)",
                    id,
                    file.path(),
                    file.bytes(),
                    file.mediaType());
        }
        jdbc.update(
                "INSERT INTO scorm_file (lesson_id, path, media, media_type) VALUES (?, ?, ?, ?)",
                id,
                "__launch__",
                unpacked.launchPath().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "text/plain");
        return id;
    }

    @Override
    public void saveTool(UUID lessonId, String url, String secret) {
        schema.ensure();
        try {
            jdbc.update(
                    "INSERT INTO lesson_tool (lesson_id, launch_url, shared_secret) VALUES (?, ?, ?)",
                    lessonId,
                    url,
                    secret);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<String> scormLaunch(UUID lessonId) {
        schema.ensure();
        List<String> rows = jdbc.query(
                "SELECT media FROM scorm_file WHERE lesson_id = ? AND path = '__launch__'",
                (rs, row) -> new String(rs.getBytes("media"), java.nio.charset.StandardCharsets.UTF_8),
                lessonId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<LessonFile> scormFile(UUID lessonId, String path) {
        schema.ensure();
        String safe = ScormZip.normalize(path);
        if ("__launch__".equals(safe)) {
            return Optional.empty();
        }
        List<LessonFile> rows = jdbc.query(
                "SELECT media, media_type FROM scorm_file WHERE lesson_id = ? AND path = ?",
                (rs, row) -> new LessonFile(LessonKind.SCORM, rs.getBytes("media"), rs.getString("media_type")),
                lessonId,
                safe);
        return rows.stream().findFirst();
    }

    @Override
    public void trackScorm(UUID lessonId, String student, String status, Integer score) {
        schema.ensure();
        jdbc.update("""
                INSERT INTO scorm_track (lesson_id, student, status, score) VALUES (?, ?, ?, ?)
                ON CONFLICT (lesson_id, student) DO UPDATE SET status = EXCLUDED.status, score = EXCLUDED.score
                """, lessonId, student, status, score);
        if ("completed".equals(status) || "passed".equals(status)) {
            jdbc.update("""
                    INSERT INTO completion (cohort_id, lesson_id, student)
                    SELECT cohort.id, lesson.id, enrollment.student
                    FROM lesson
                    JOIN module ON module.id = lesson.module_id
                    JOIN cohort ON cohort.course_id = module.course_id
                    JOIN enrollment ON enrollment.cohort_id = cohort.id AND enrollment.student = ?
                    WHERE lesson.id = ?
                    ON CONFLICT DO NOTHING
                    """, student, lessonId);
        }
    }

    @Override
    public Optional<String> toolUrl(UUID lessonId) {
        schema.ensure();
        List<String> rows = jdbc.query(
                "SELECT launch_url FROM lesson_tool WHERE lesson_id = ?",
                (rs, row) -> rs.getString("launch_url"),
                lessonId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<String> toolSecret(UUID lessonId) {
        schema.ensure();
        List<String> rows = jdbc.query(
                "SELECT shared_secret FROM lesson_tool WHERE lesson_id = ?",
                (rs, row) -> rs.getString("shared_secret"),
                lessonId);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public void reorder(UUID courseId, List<ModuleOrder> modules) {
        schema.ensure();
        int position = 1;
        for (ModuleOrder module : modules) {
            int moved = jdbc.update(
                    "UPDATE module SET position = ? WHERE id = ? AND course_id = ?",
                    position,
                    module.id(),
                    courseId);
            if (moved == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            position++;
            int lessonPosition = 1;
            for (UUID lessonId : module.lessons()) {
                int lesson = jdbc.update("""
                        UPDATE lesson SET position = ?, module_id = ?
                        WHERE id = ? AND module_id IN (SELECT id FROM module WHERE course_id = ?)
                        """, lessonPosition, module.id(), lessonId, courseId);
                if (lesson == 0) {
                    throw new Rejection(Failure.NOT_FOUND);
                }
                lessonPosition++;
            }
        }
    }

    @Override
    public void reviseLesson(UUID lessonId, String title, String markdown) {
        schema.ensure();
        try {
            int updated = jdbc.update("UPDATE lesson SET title = ? WHERE id = ?", title, lessonId);
            if (updated == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            if (markdown == null) {
                return;
            }
            int text = jdbc.update(
                    "UPDATE lesson SET markdown = ? WHERE id = ? AND kind = 'markdown'",
                    markdown,
                    lessonId);
            if (text == 0) {
                throw new Rejection(Failure.FILE_ONLY);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public UUID addAsset(UUID lessonId, String name, byte[] media, String mediaType) {
        schema.ensure();
        try {
            if (lesson(lessonId).isEmpty()) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO lesson_asset (id, lesson_id, name, media, media_type) VALUES (?, ?, ?, ?, ?)",
                    id,
                    lessonId,
                    name,
                    media,
                    mediaType);
            return id;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<LessonAsset> assets(UUID lessonId) {
        schema.ensure();
        try {
            return jdbc.query(
                    "SELECT id, name, media_type FROM lesson_asset WHERE lesson_id = ? ORDER BY name",
                    (rs, row) -> new LessonAsset(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("media_type")),
                    lessonId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public Optional<LessonFile> asset(UUID lessonId, UUID assetId) {
        schema.ensure();
        try {
            List<LessonFile> rows = jdbc.query(
                    "SELECT media, media_type FROM lesson_asset WHERE lesson_id = ? AND id = ?",
                    (rs, row) -> new LessonFile(
                            LessonKind.PDF,
                            rs.getBytes("media"),
                            rs.getString("media_type")),
                    lessonId,
                    assetId);
            return rows.stream().findFirst();
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void removeAsset(UUID lessonId, UUID assetId) {
        schema.ensure();
        try {
            int removed = jdbc.update(
                    "DELETE FROM lesson_asset WHERE lesson_id = ? AND id = ?",
                    lessonId,
                    assetId);
            if (removed == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private boolean courseExists(UUID id) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = ?", Integer.class, id);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private boolean moduleExists(UUID id) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM module WHERE id = ?", Integer.class, id);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private static LessonKind kind(String wire) {
        return LessonKind.parse(wire).orElseThrow(() -> new IllegalStateException("unknown lesson kind"));
    }

    private record CourseSeed(UUID id, String name) {
    }

    private record ModuleSeed(UUID id, UUID courseId, String name) {
    }

    private record StoredFile(String kind, byte[] bytes, String mediaType) {
    }

}
