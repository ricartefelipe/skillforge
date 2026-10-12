package skillforge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

public final class InstitutionalRole {

    public static final Set<String> NAMES = Set.of("aluno", "docente", "administracao");

    private InstitutionalRole() {
    }

    public static Optional<String> only(Collection<String> roles) {
        List<String> known = new ArrayList<>();
        for (String role : roles) {
            if (NAMES.contains(role) && !known.contains(role)) {
                known.add(role);
            }
        }
        if (known.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(known.get(0));
    }

    public static List<String> fromToken(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> access)) {
            return List.of();
        }
        Object roles = access.get("roles");
        if (!(roles instanceof List<?> values)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String name) {
                names.add(name);
            }
        }
        return names;
    }

}
