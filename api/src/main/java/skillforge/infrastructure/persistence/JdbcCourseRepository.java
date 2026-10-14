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

import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.Lesson;
import skillforge.domain.catalog.LessonContent;
import skillforge.domain.catalog.LessonFile;
import skillforge.domain.catalog.LessonKind;
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
