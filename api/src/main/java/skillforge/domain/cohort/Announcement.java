package skillforge.domain.cohort;

import java.util.Objects;
import java.util.UUID;

public record Announcement(UUID id, String title, String body) {

    public Announcement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }

}
