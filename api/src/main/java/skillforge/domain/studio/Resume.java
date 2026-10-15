package skillforge.domain.studio;

import java.util.Objects;
import java.util.UUID;

public record Resume(UUID lessonId, String title) {

    public Resume {
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(title, "title");
    }

}
