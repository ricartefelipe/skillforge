package skillforge.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record Lesson(UUID id, String title, LessonKind kind) {

    public Lesson {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(kind, "kind");
    }

}
