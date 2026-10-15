package skillforge.domain.studio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InboxNote(UUID id, String body, Instant createdAt, boolean read) {

    public InboxNote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
    }

}
