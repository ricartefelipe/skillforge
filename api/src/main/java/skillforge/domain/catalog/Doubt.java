package skillforge.domain.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Doubt(UUID id, String author, String title, String body, List<Reply> replies) {

    public Doubt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        replies = List.copyOf(replies);
    }

}
