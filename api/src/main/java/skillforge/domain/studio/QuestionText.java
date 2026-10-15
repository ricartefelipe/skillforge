package skillforge.domain.studio;

import java.util.List;
import java.util.UUID;

public record QuestionText(UUID id, String prompt, List<ChoiceText> choices) {

    public QuestionText {
        choices = List.copyOf(choices);
    }

    public record ChoiceText(UUID id, String label) {
    }

}
