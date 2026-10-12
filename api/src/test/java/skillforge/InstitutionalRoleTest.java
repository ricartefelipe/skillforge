package skillforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class InstitutionalRoleTest {

    @Test
    void keepsASingleInstitutionalRole() {
        assertThat(InstitutionalRole.only(List.of("aluno", "offline_access"))).contains("aluno");
        assertThat(InstitutionalRole.only(List.of("docente"))).contains("docente");
        assertThat(InstitutionalRole.only(List.of("administracao"))).contains("administracao");
    }

    @Test
    void refusesZeroOrSeveralInstitutionalRoles() {
        assertThat(InstitutionalRole.only(List.of("offline_access"))).isEmpty();
        assertThat(InstitutionalRole.only(List.of())).isEmpty();
        assertThat(InstitutionalRole.only(List.of("aluno", "docente"))).isEmpty();
    }

    @Test
    void readsRealmRolesFromTheToken() {
        Jwt jwt = token(Map.of("roles", List.of("aluno", "default-roles-skillforge")));

        assertThat(InstitutionalRole.fromToken(jwt)).containsExactly("aluno", "default-roles-skillforge");
    }

    @Test
    void readsNothingWhenTheTokenHasNoRealmAccess() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("aluno")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(30))
                .build();

        assertThat(InstitutionalRole.fromToken(jwt)).isEmpty();
    }

    private static Jwt token(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", realmAccess)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(30))
                .build();
    }

}
