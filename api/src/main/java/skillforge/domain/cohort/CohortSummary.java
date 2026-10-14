package skillforge.domain.cohort;

import java.util.Objects;
import java.util.UUID;

public record CohortSummary(UUID id, String name, UUID courseId, String courseName, String teacher) {

    public CohortSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(courseName, "courseName");
        Objects.requireNonNull(teacher, "teacher");
    }

}
