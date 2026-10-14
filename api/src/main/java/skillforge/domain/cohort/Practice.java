package skillforge.domain.cohort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Practice(UUID id, String title, String prompt, List<Delivery> deliveries) {

    public Practice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(prompt, "prompt");
        deliveries = List.copyOf(deliveries);
    }

}
