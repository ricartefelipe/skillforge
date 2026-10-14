package skillforge.domain.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Module(UUID id, String name, List<Lesson> lessons) {

    public Module {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        lessons = List.copyOf(lessons);
    }

}
