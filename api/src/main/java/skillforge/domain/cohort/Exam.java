package skillforge.domain.cohort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Exam(
        UUID id,
        String title,
        Instant dueAt,
        Integer minutes,
        int attemptsAllowed,
        int taken,
        List<Question> questions,
        List<ExamScore> scores) {

    public Exam {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        questions = List.copyOf(questions);
        scores = List.copyOf(scores);
    }

    public boolean closed(Instant now) {
        return dueAt != null && !dueAt.isAfter(now);
    }

}
