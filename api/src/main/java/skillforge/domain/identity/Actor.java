package skillforge.domain.identity;

import java.util.Objects;

import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

public record Actor(Role role, String username) {

    public Actor {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(username, "username");
    }

    public void requireAdministration() {
        if (role != Role.ADMINISTRACAO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
    }

    public void requireStudent() {
        if (role != Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
    }

}
