package skillforge.domain.studio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StudyGroup(UUID id, String name, List<String> members) {

    public StudyGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        members = List.copyOf(members);
    }

}
