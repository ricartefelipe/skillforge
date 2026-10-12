package skillforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SkillforgeTokensTest {

    @Test
    void acceptsTheSkillforgeAudience() {
        Jwt jwt = jwt(List.of("skillforge", "account"));

        assertThat(SkillforgeTokens.audienceIncludesSkillforge(jwt)).isTrue();
        assertThat(SkillforgeTokens.validator("http://localhost:8180/realms/skillforge").validate(jwt).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsATokenForAnotherAudience() {
        Jwt jwt = jwt(List.of("account"));

        assertThat(SkillforgeTokens.audienceIncludesSkillforge(jwt)).isFalse();
        assertThat(SkillforgeTokens.validator("http://localhost:8180/realms/skillforge").validate(jwt).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        Jwt jwt = jwt(List.of("skillforge"));

        assertThat(SkillforgeTokens.validator("http://other/realms/skillforge").validate(jwt).hasErrors()).isTrue();
    }

    private static Jwt jwt(List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("http://localhost:8180/realms/skillforge")
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

}
