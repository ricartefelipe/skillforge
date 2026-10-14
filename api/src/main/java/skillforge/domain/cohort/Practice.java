package skillforge.domain.cohort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Practice(
        UUID id,
        String title,
        String prompt,
        Instant dueAt,
        List<Criterion> criteria,
        List<Delivery> deliveries) {

    public Practice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(prompt, "prompt");
        criteria = List.copyOf(criteria);
        deliveries = List.copyOf(deliveries);
    }

    public boolean closed(Instant now) {
        return dueAt != null && !dueAt.isAfter(now);
    }

}
