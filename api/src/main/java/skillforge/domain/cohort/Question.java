package skillforge.domain.cohort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Question(UUID id, String prompt, List<Choice> choices) {

    public Question {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(prompt, "prompt");
        choices = List.copyOf(choices);
    }

}
