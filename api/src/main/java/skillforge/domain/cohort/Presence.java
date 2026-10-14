package skillforge.domain.cohort;

import java.util.Objects;

public record Presence(String student, boolean present) {

    public Presence {
        Objects.requireNonNull(student, "student");
    }

}
