package skillforge.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record Reply(UUID id, String author, String body) {

    public Reply {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(body, "body");
    }

}
