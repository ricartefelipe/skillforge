package skillforge.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import skillforge.domain.catalog.Doubt;
import skillforge.domain.catalog.ForumRepository;
import skillforge.domain.catalog.Reply;

@Component
class JdbcForumRepository implements ForumRepository {

    private final Schema schema;

    private final JdbcTemplate jdbc;

    JdbcForumRepository(Schema schema, JdbcTemplate jdbc) {
        this.schema = schema;
        this.jdbc = jdbc;
    }

    @Override
    public List<Doubt> list(UUID lessonId) {
        schema.ensure();
        try {
            List<DoubtSeed> seeds = jdbc.query(
                    "SELECT id, author, title, body FROM doubt WHERE lesson_id = ? ORDER BY title",
                    (rs, row) -> new DoubtSeed(
                            rs.getObject("id", UUID.class),
                            rs.getString("author"),
                            rs.getString("title"),
                            rs.getString("body")),
                    lessonId);
            Map<UUID, List<Reply>> replies = new LinkedHashMap<>();
            for (DoubtSeed seed : seeds) {
                replies.put(seed.id(), new ArrayList<>());
            }
            if (!seeds.isEmpty()) {
                jdbc.query("""
                        SELECT reply.id, reply.doubt_id, reply.author, reply.body
                        FROM reply
                        JOIN doubt ON doubt.id = reply.doubt_id
                        WHERE doubt.lesson_id = ?
                        ORDER BY reply.author
                        """, (rs, row) -> {
                            List<Reply> found = replies.get(rs.getObject("doubt_id", UUID.class));
                            if (found != null) {
                                found.add(new Reply(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("author"),
                                        rs.getString("body")));
                            }
                            return null;
                        }, lessonId);
            }
            List<Doubt> doubts = new ArrayList<>();
            for (DoubtSeed seed : seeds) {
                doubts.add(new Doubt(seed.id(), seed.author(), seed.title(), seed.body(), replies.get(seed.id())));
            }
            return List.copyOf(doubts);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public UUID open(UUID lessonId, String author, String title, String body) {
        schema.ensure();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO doubt (id, lesson_id, author, title, body) VALUES (?, ?, ?, ?, ?)",
                    id,
                    lessonId,
                    author,
                    title,
                    body);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
        return id;
    }

    @Override
    public UUID reply(UUID doubtId, String author, String body) {
        schema.ensure();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO reply (id, doubt_id, author, body) VALUES (?, ?, ?, ?)",
                    id,
                    doubtId,
                    author,
                    body);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
        return id;
    }

    @Override
    public Optional<UUID> courseOfLesson(UUID lessonId) {
        schema.ensure();
        try {
            List<UUID> rows = jdbc.query("""
                    SELECT module.course_id
                    FROM lesson
                    JOIN module ON module.id = lesson.module_id
                    WHERE lesson.id = ?
                    """, (rs, row) -> rs.getObject("course_id", UUID.class), lessonId);
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
    public boolean doubtOnLesson(UUID lessonId, UUID doubtId) {
        schema.ensure();
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM doubt WHERE id = ? AND lesson_id = ?",
                    Integer.class,
                    doubtId,
                    lessonId);
            return count != null && count > 0;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private record DoubtSeed(UUID id, String author, String title, String body) {
    }

}
