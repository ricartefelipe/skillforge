package skillforge.domain.catalog;

import java.util.Optional;

public enum LessonKind {

    MARKDOWN("markdown"),
    PDF("pdf"),
    VIDEO("video");

    private final String wire;

    LessonKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Optional<LessonKind> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (LessonKind kind : values()) {
            if (kind.wire.equals(raw)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

}
