package skillforge.domain.catalog;

import java.util.Arrays;
import java.util.Objects;

public record LessonFile(LessonKind kind, byte[] content, String mediaType) {

    public LessonFile {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LessonFile file)) {
            return false;
        }
        return kind == file.kind && mediaType.equals(file.mediaType) && Arrays.equals(content, file.content());
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, mediaType, Arrays.hashCode(content));
    }

}
