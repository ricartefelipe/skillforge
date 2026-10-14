package skillforge.domain.cohort;

import java.util.Objects;

public record Progress(String student, int completed, int total) {

    public Progress {
        Objects.requireNonNull(student, "student");
    }

}
