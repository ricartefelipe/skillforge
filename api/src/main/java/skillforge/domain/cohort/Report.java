package skillforge.domain.cohort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Report(UUID cohortId, String cohortName, String courseName, List<Practice> practices) {

    public Report {
        Objects.requireNonNull(cohortId, "cohortId");
        Objects.requireNonNull(cohortName, "cohortName");
        Objects.requireNonNull(courseName, "courseName");
        practices = List.copyOf(practices);
    }

}
