package skillforge.domain.cohort;

import java.util.Objects;

public record ExamScore(String student, int grade) {

    public ExamScore {
        Objects.requireNonNull(student, "student");
    }

}
