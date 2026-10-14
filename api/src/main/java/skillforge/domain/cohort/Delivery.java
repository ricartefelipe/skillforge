package skillforge.domain.cohort;

import java.util.Objects;

public record Delivery(String student, String text, String feedback) {

    public Delivery {
        Objects.requireNonNull(student, "student");
        Objects.requireNonNull(text, "text");
    }

    public boolean returned() {
        return feedback != null;
    }

}
