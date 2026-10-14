package skillforge.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

public final class RealmAccess {

    private RealmAccess() {
    }

    public static List<String> roles(Jwt jwt) {
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
        return List.copyOf(names);
    }

    public static Actor requireActor(Jwt jwt) {
        Role role = Role.only(roles(jwt)).orElseThrow(() -> new Rejection(Failure.FORBIDDEN));
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null) {
            username = "";
        }
        return new Actor(role, username.trim());
    }

    public static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null) {
            return "";
        }
        return username;
    }

}
