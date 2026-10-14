package skillforge.interfaces.http;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import skillforge.domain.identity.Actor;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;
import skillforge.infrastructure.security.RealmAccess;

final class RequestActor {

    private RequestActor() {
    }

    static Actor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        return RealmAccess.requireActor(jwt);
    }

}
