package skillforge;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
class CatalogStore {

    private final javax.sql.DataSource dataSource;

    private final JdbcTemplate jdbc;

    // The script is idempotent. Skip it after the first success so a later outage is a failed call.
    private volatile boolean schemaReady;

    CatalogStore(javax.sql.DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    UUID createCourse(String name) {
        ensureSchema();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO course (id, name) VALUES (?, ?)", id, name);
        }
        catch (DuplicateKeyException ex) {
            throw new CatalogRejection("duplicate-course", HttpStatus.CONFLICT);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
        return id;
    }

    UUID createModule(UUID courseId, String name) {
        ensureSchema();
        if (!courseExists(courseId)) {
            throw new CatalogRejection("not-found", HttpStatus.NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO module (id, course_id, name, position)
                    SELECT ?, ?, ?, COALESCE(MAX(position), 0) + 1 FROM module WHERE course_id = ?
                    """, id, courseId, name, courseId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
        return id;
    }

    UUID createLesson(UUID moduleId, String title, LessonContent.Accepted content) {
        ensureSchema();
        if (!moduleExists(moduleId)) {
            throw new CatalogRejection("not-found", HttpStatus.NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO lesson (id, module_id, title, position, kind, markdown, media, media_type)
                    SELECT ?, ?, ?, COALESCE(MAX(position), 0) + 1, ?, ?, ?, ? FROM lesson WHERE module_id = ?
                    """, id, moduleId, title, content.kind(), content.markdown(), content.media(),
                    content.mediaType(), moduleId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
        return id;
    }

    List<CourseView> courses() {
        ensureSchema();
        try {
            List<CourseView> courses = jdbc.query(
                    "SELECT id, name FROM course ORDER BY name",
                    (rs, row) -> new CourseView(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            new ArrayList<>()));
            Map<UUID, CourseView> byCourse = new LinkedHashMap<>();
            for (CourseView course : courses) {
                byCourse.put(course.id(), course);
            }
            List<ModuleView> modules = jdbc.query(
                    "SELECT id, course_id, name FROM module ORDER BY position, name",
                    (rs, row) -> new ModuleView(
                            rs.getObject("id", UUID.class),
                            rs.getObject("course_id", UUID.class),
                            rs.getString("name"),
                            new ArrayList<>()));
            Map<UUID, ModuleView> byModule = new LinkedHashMap<>();
            for (ModuleView module : modules) {
                byModule.put(module.id(), module);
                CourseView course = byCourse.get(module.courseId());
                if (course != null) {
                    course.modules().add(module);
                }
            }
            jdbc.query(
                    "SELECT id, module_id, title, kind FROM lesson ORDER BY position, title",
                    (rs, row) -> {
                        ModuleView module = byModule.get(rs.getObject("module_id", UUID.class));
                        if (module != null) {
                            module.lessons().add(new LessonSummary(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("title"),
                                    rs.getString("kind")));
                        }
                        return null;
                    });
            return courses;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    Optional<LessonView> lesson(UUID id) {
        ensureSchema();
        try {
            List<LessonView> rows = jdbc.query(
                    "SELECT id, title, kind, markdown FROM lesson WHERE id = ?",
                    (rs, row) -> new LessonView(
                            rs.getObject("id", UUID.class),
                            rs.getString("title"),
                            rs.getString("kind"),
                            rs.getString("markdown")),
                    id);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(rows.get(0));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    Optional<Media> media(UUID id) {
        ensureSchema();
        try {
            List<Media> rows = jdbc.query(
                    "SELECT kind, media, media_type FROM lesson WHERE id = ?",
                    (rs, row) -> new Media(rs.getString("kind"), rs.getBytes("media"), rs.getString("media_type")),
                    id);
            if (rows.isEmpty() || "markdown".equals(rows.get(0).kind()) || rows.get(0).bytes() == null) {
                return Optional.empty();
            }
            return Optional.of(rows.get(0));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    private boolean courseExists(UUID id) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = ?", Integer.class, id);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    private boolean moduleExists(UUID id) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM module WHERE id = ?", Integer.class, id);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new CatalogUnavailable(ex);
        }
    }

    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            // Tables are created on use. A closed database must not stop liveness.
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("catalog.sql"));
                schemaReady = true;
            }
            catch (SQLException ex) {
                throw new CatalogUnavailable(ex);
            }
        }
    }

    record CourseView(UUID id, String name, List<ModuleView> modules) {
    }

    record ModuleView(UUID id, UUID courseId, String name, List<LessonSummary> lessons) {
    }

    record LessonSummary(UUID id, String title, String kind) {
    }

    record LessonView(UUID id, String title, String kind, String markdown) {
    }

    record Media(String kind, byte[] bytes, String mediaType) {
    }

}
