package skillforge.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record LessonText(UUID id, String title, LessonKind kind, String markdown) {

    public LessonText {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(kind, "kind");
    }

}
