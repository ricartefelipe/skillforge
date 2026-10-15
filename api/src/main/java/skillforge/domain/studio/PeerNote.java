package skillforge.domain.studio;

import java.util.Objects;

public record PeerNote(String author, String reviewer, String comment, int mark) {

    public PeerNote {
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(reviewer, "reviewer");
        Objects.requireNonNull(comment, "comment");
    }

}
