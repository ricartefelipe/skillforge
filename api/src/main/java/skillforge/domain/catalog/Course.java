package skillforge.domain.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Course(UUID id, String name, List<Module> modules) {

    public Course {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        modules = List.copyOf(modules);
    }

}
