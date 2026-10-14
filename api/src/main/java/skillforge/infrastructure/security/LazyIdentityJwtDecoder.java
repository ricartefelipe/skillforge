package skillforge.infrastructure.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

final class LazyIdentityJwtDecoder implements JwtDecoder {

    private final String issuer;

    private volatile JwtDecoder delegate;

    LazyIdentityJwtDecoder(String issuer) {
        this.issuer = issuer;
    }

    @Override
    public Jwt decode(String token) {
        try {
            return decoder().decode(token);
        }
        catch (JwtException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            throw new BadJwtException("Identity provider did not accept the token", ex);
        }
    }

    private JwtDecoder decoder() {
        JwtDecoder current = delegate;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (delegate == null) {
                NimbusJwtDecoder created = JwtDecoders.fromIssuerLocation(issuer);
                created.setJwtValidator(SkillforgeTokens.validator(issuer));
                delegate = created;
            }
            return delegate;
        }
    }

}
