package skillforge.domain.studio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import skillforge.domain.cohort.ExamDraft;

public record BankQuestion(UUID id, String prompt, List<ExamDraft.ChoiceDraft> choices) {

    public BankQuestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(prompt, "prompt");
        choices = List.copyOf(choices);
    }

}
