package skillforge.domain.cohort;

import java.time.Instant;
import java.util.List;

public record ExamDraft(
        String title,
        Instant dueAt,
        Integer minutes,
        int attemptsAllowed,
        List<QuestionDraft> questions) {

    public ExamDraft {
        questions = List.copyOf(questions);
    }

    public record QuestionDraft(String prompt, List<ChoiceDraft> choices) {

        public QuestionDraft {
            choices = List.copyOf(choices);
        }

    }

    public record ChoiceDraft(String label, boolean correct) {
    }

}
