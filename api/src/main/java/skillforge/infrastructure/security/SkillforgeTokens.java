package skillforge.infrastructure.security;

import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

public final class SkillforgeTokens {

    static final String AUDIENCE = "skillforge";

    private SkillforgeTokens() {
    }

    public static boolean audienceIncludesSkillforge(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        return audience != null && audience.contains(AUDIENCE);
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer) {
        OAuth2TokenValidator<Jwt> audience = token -> {
            if (audienceIncludesSkillforge(token)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience", null));
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience);
    }

}
