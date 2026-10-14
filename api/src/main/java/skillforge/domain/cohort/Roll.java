package skillforge.domain.cohort;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record Roll(LocalDate heldOn, List<Presence> marks) {

    public Roll {
        Objects.requireNonNull(heldOn, "heldOn");
        marks = List.copyOf(marks);
    }

}
