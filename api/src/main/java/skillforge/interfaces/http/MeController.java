package skillforge.interfaces.http;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import skillforge.domain.identity.Role;
import skillforge.infrastructure.security.RealmAccess;

@RestController
class MeController {

    @GetMapping("/me")
    ResponseEntity<MeBody> me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Role> role = Role.only(RealmAccess.roles(jwt));
        String name = RealmAccess.displayName(jwt);
        if (role.isEmpty() || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(new MeBody(name, role.get().wire()));
    }

    record MeBody(String name, String role) {
    }

}
