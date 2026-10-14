package skillforge;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
class CatalogWhenDatabaseIsDownTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theCatalogRefusesWorkAndLivenessStaysUp() throws Exception {
        HealthAssertions.assertProbe(mvc, "/actuator/health/liveness", HttpStatus.OK.value(), "UP");

        mvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forja\"}")
                        .with(jwt().jwt(token -> token
                                .claim("realm_access", Map.of("roles", List.of("aluno"))))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/courses").with(jwt().jwt(token -> token
                        .claim("realm_access", Map.of("roles", List.of("administracao"))))))
                .andExpect(status().isServiceUnavailable());

        mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Manhã","courseId":"00000000-0000-0000-0000-000000000000","teacher":"docente"}
                                """)
                        .with(jwt().jwt(token -> token
                                .claim("preferred_username", "aluno")
                                .claim("realm_access", Map.of("roles", List.of("aluno"))))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/cohorts").with(jwt().jwt(token -> token
                        .claim("preferred_username", "administracao")
                        .claim("realm_access", Map.of("roles", List.of("administracao"))))))
                .andExpect(status().isServiceUnavailable());
    }

}
