package skillforge.domain.studio;

import java.util.List;
import java.util.Objects;

public record PeerWork(String author, String text, List<PeerNote> reviews) {

    public PeerWork {
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        reviews = List.copyOf(reviews);
    }

}
