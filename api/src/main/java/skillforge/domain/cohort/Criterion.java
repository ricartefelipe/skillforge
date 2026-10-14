package skillforge.domain.cohort;

import java.util.Objects;
import java.util.UUID;

public record Criterion(UUID id, String name, int points) {

    public Criterion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

}
