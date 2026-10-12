package skillforge;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    @GetMapping("/me")
    ResponseEntity<Me> me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<String> role = InstitutionalRole.only(InstitutionalRole.fromToken(jwt));
        String name = nameOf(jwt);
        if (role.isEmpty() || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(new Me(name, role.get()));
    }

    private static String nameOf(Jwt jwt) {
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

    record Me(String name, String role) {
    }

}
