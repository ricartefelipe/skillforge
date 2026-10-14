package skillforge;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

@Testcontainers
@SpringBootTest(properties = {
        "skillforge.issuer=http://127.0.0.1:1/realms/skillforge",
        "skillforge.identity-timeout=500ms"
})
@AutoConfigureMockMvc
class CohortAccessTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyCohorts() throws Exception {
        mvc.perform(get("/cohorts").with(as("administracao", "administracao"))).andExpect(status().isOk());
        jdbc.update("TRUNCATE enrollment, cohort, lesson, module, course");
    }

    @Test
    void anonymousCallerCannotReadCohorts() throws Exception {
        mvc.perform(get("/cohorts")).andExpect(status().isUnauthorized());
    }

    @Test
    void aStudentCannotOpenOrEnroll() throws Exception {
        mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJson("Manhã", "00000000-0000-0000-0000-000000000000", "docente"))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));

        mvc.perform(post("/cohorts/00000000-0000-0000-0000-000000000000/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"aluno\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isForbidden());
    }

    @Test
    void severalRolesCannotReadCohorts() throws Exception {
        mvc.perform(get("/cohorts").with(jwt().jwt(token -> token
                        .claim("preferred_username", "aluno")
                        .claim("realm_access", Map.of("roles", List.of("aluno", "docente"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrollmentIsolatesWhatEachRoleSees() throws Exception {
        String courseId = createCourse("Forja");
        String moduleId = createModule(courseId, "Fundamentos");
        createMarkdown(moduleId, "O que é");
        String morning = openCohort("Manhã", courseId, "docente");
        String evening = openCohort("Noite", courseId, "outro");
        enroll(morning, "aluno");
        enroll(evening, "colega");

        mvc.perform(get("/cohorts").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Manhã"))
                .andExpect(jsonPath("$[0].teacher").value("docente"))
                .andExpect(jsonPath("$[0].courseName").value("Forja"))
                .andExpect(jsonPath("$[0].students").doesNotExist());

        mvc.perform(get("/cohorts/" + morning).with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.modules[0].lessons[0].title").value("O que é"))
                .andExpect(jsonPath("$.students").doesNotExist())
                .andExpect(jsonPath("$.completed").doesNotExist());

        mvc.perform(get("/cohorts/" + evening).with(as("aluno", "aluno")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/cohorts").with(as("aluno", "colega")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Noite"));

        mvc.perform(get("/cohorts").with(as("docente", "docente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(morning))
                .andExpect(jsonPath("$[0].students[0]").value("aluno"));

        mvc.perform(get("/cohorts/" + evening).with(as("docente", "docente")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/cohorts").with(as("docente", "outro")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(evening))
                .andExpect(jsonPath("$[0].students[0]").value("colega"));

        mvc.perform(get("/cohorts").with(as("administracao", "administracao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aBlankRepeatedOrUnknownCohortIsRejected() throws Exception {
        String courseId = createCourse("Forja");

        mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJson("  ", courseId, "docente"))
                        .with(as("administracao", "administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank-cohort"));

        mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJson("Manhã", courseId, "  "))
                        .with(as("administracao", "administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank-teacher"));

        mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJson("a".repeat(121), courseId, "docente"))
                        .with(as("administracao", "administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("too-large"));

        mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJson("Manhã", "00000000-0000-0000-0000-000000000000", "docente"))
                        .with(as("administracao", "administracao")))
                .andExpect(status().isNotFound());

        String cohortId = openCohort("Manhã", courseId, "docente");
        mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJson("manhã", courseId, "outro"))
                        .with(as("administracao", "administracao")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate-cohort"));

        String otherCourse = createCourse("Oficina");
        openCohort("Manhã", otherCourse, "docente");

        enroll(cohortId, "aluno");
        mvc.perform(post("/cohorts/" + cohortId + "/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"  \"}")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank-student"));

        mvc.perform(post("/cohorts/" + cohortId + "/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"Aluno\"}")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("already-enrolled"));

        mvc.perform(post("/cohorts/00000000-0000-0000-0000-000000000000/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"colega\"}")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isNotFound());
    }

    private String createCourse(String name) throws Exception {
        MvcResult result = mvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createModule(String courseId, String name) throws Exception {
        MvcResult result = mvc.perform(post("/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createMarkdown(String moduleId, String title) throws Exception {
        mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .param("title", title)
                        .param("kind", "markdown")
                        .param("markdown", "Oficina")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated());
    }

    private String openCohort(String name, String courseId, String teacher) throws Exception {
        MvcResult result = mvc.perform(post("/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJson(name, courseId, teacher))
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void enroll(String cohortId, String student) throws Exception {
        mvc.perform(post("/cohorts/" + cohortId + "/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"" + student + "\"}")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated());
    }

    private static String openJson(String name, String courseId, String teacher) {
        return "{\"name\":\"" + name + "\",\"courseId\":\"" + courseId + "\",\"teacher\":\"" + teacher + "\"}";
    }

    private static RequestPostProcessor as(String role, String username) {
        return jwt().jwt(token -> token
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of(role))));
    }

}
