package skillforge.domain.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public enum Role {

    ALUNO("aluno"),
    DOCENTE("docente"),
    ADMINISTRACAO("administracao");

    private final String wire;

    Role(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Optional<Role> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (Role role : values()) {
            if (role.wire.equals(raw)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    public static Optional<Role> only(Collection<String> roles) {
        List<Role> known = new ArrayList<>();
        for (String role : roles) {
            parse(role).ifPresent(parsed -> {
                if (!known.contains(parsed)) {
                    known.add(parsed);
                }
            });
        }
        if (known.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(known.get(0));
    }

}
