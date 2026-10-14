package skillforge.domain.cohort;

import java.time.LocalDate;
import java.util.Objects;

public record Certificate(String student, String courseName, String cohortName, String average, LocalDate issuedOn) {

    public Certificate {
        Objects.requireNonNull(student, "student");
        Objects.requireNonNull(courseName, "courseName");
        Objects.requireNonNull(cohortName, "cohortName");
        Objects.requireNonNull(average, "average");
        Objects.requireNonNull(issuedOn, "issuedOn");
    }

}
