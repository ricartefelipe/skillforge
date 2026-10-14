package skillforge.domain.cohort;

import java.util.Objects;
import java.util.UUID;

public record Choice(UUID id, String label, boolean correct) {

    public Choice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
    }

}
