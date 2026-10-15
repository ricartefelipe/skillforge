package skillforge.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record LessonAsset(UUID id, String name, String mediaType) {

    public LessonAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mediaType, "mediaType");
    }

}
