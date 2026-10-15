package skillforge.domain.catalog;

import java.util.List;
import java.util.UUID;

public record ModuleOrder(UUID id, List<UUID> lessons) {

    public ModuleOrder {
        lessons = List.copyOf(lessons);
    }

}
