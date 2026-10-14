package skillforge.domain.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumRepository {

    List<Doubt> list(UUID lessonId);

    UUID open(UUID lessonId, String author, String title, String body);

    UUID reply(UUID doubtId, String author, String body);

    Optional<UUID> courseOfLesson(UUID lessonId);

    boolean doubtOnLesson(UUID lessonId, UUID doubtId);

}
