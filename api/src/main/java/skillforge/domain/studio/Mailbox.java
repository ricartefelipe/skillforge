package skillforge.domain.studio;

import java.util.List;

public record Mailbox(String email, List<String> sent) {

    public Mailbox {
        sent = List.copyOf(sent == null ? List.of() : sent);
    }

}
