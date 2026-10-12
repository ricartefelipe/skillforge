package skillforge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/skillforge",
        "spring.datasource.username=skillforge",
        "spring.datasource.password=closed",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.datasource.hikari.validation-timeout=500",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.maximum-pool-size=1",
        "skillforge.issuer=http://127.0.0.1:1/realms/skillforge",
        "skillforge.identity-timeout=500ms"
})
@AutoConfigureMockMvc
class MeAccessTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousCallerIsRejected() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void probesStayOpenWithoutASession() throws Exception {
        HealthAssertions.assertProbe(mvc, "/actuator/health/liveness", HttpStatus.OK.value(), "UP");
    }

    @ParameterizedTest
    @CsvSource({
            "aluno, Aluno",
            "docente, Docente",
            "administracao, Administração"
    })
    void knownRoleIsReturned(String role, String name) throws Exception {
        mvc.perform(get("/me").with(jwt().jwt(token -> token
                        .claim("name", name)
                        .claim("realm_access", Map.of("roles", List.of(role, "offline_access"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void severalInstitutionalRolesAreRejected() throws Exception {
        mvc.perform(get("/me").with(jwt().jwt(token -> token
                        .claim("name", "Aluno")
                        .claim("realm_access", Map.of("roles", List.of("aluno", "docente"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenWithoutAnInstitutionalRoleIsRejected() throws Exception {
        mvc.perform(get("/me").with(jwt().jwt(token -> token
                        .claim("name", "Aluno")
                        .claim("realm_access", Map.of("roles", List.of("offline_access"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRoleWithoutANameIsRejected() throws Exception {
        mvc.perform(get("/me").with(jwt().jwt(token -> token
                        .claim("realm_access", Map.of("roles", List.of("aluno"))))))
                .andExpect(status().isForbidden());
    }

}
