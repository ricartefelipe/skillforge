package skillforge.domain.cohort;

import java.util.Map;
import java.util.UUID;

import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

public final class ExamGrade {

    private ExamGrade() {
    }

    public static int of(Exam exam, Map<UUID, UUID> answers) {
        if (answers.size() != exam.questions().size()) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
        int correct = 0;
        for (Question question : exam.questions()) {
            UUID chosen = answers.get(question.id());
            if (chosen == null) {
                throw new Rejection(Failure.INVALID_EXAM);
            }
            Choice match = null;
            for (Choice choice : question.choices()) {
                if (choice.id().equals(chosen)) {
                    match = choice;
                }
            }
            if (match == null) {
                throw new Rejection(Failure.INVALID_EXAM);
            }
            if (match.correct()) {
                correct++;
            }
        }
        return (int) Math.round(correct * 10.0 / exam.questions().size());
    }

}
