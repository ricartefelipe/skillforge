package skillforge.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void keepsASingleInstitutionalRole() {
        assertThat(Role.only(List.of("aluno", "offline_access"))).contains(Role.ALUNO);
        assertThat(Role.only(List.of("docente"))).contains(Role.DOCENTE);
        assertThat(Role.only(List.of("administracao"))).contains(Role.ADMINISTRACAO);
    }

    @Test
    void refusesZeroOrSeveralInstitutionalRoles() {
        assertThat(Role.only(List.of("offline_access"))).isEmpty();
        assertThat(Role.only(List.of())).isEmpty();
        assertThat(Role.only(List.of("aluno", "docente"))).isEmpty();
    }

}
