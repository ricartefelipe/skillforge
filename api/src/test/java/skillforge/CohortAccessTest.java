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
        jdbc.update("""
                TRUNCATE attempt_answer, attempt, choice, question, exam, reply, doubt, submission, practice,
                completion, enrollment, cohort, lesson, module, course
                """);
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
                .andExpect(jsonPath("$[0].students").doesNotExist())
                .andExpect(jsonPath("$[0].completed").value(0))
                .andExpect(jsonPath("$[0].total").value(1));

        mvc.perform(get("/cohorts/" + morning).with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.modules[0].lessons[0].title").value("O que é"))
                .andExpect(jsonPath("$.course.modules[0].lessons[0].completed").value(false))
                .andExpect(jsonPath("$.students").doesNotExist())
                .andExpect(jsonPath("$.grade").doesNotExist());

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
                .andExpect(jsonPath("$[0].students[0].student").value("aluno"))
                .andExpect(jsonPath("$[0].students[0].completed").value(0));

        mvc.perform(get("/cohorts/" + evening).with(as("docente", "docente")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/cohorts").with(as("docente", "outro")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(evening))
                .andExpect(jsonPath("$[0].students[0].student").value("colega"));

        mvc.perform(get("/cohorts").with(as("administracao", "administracao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aStudentKeepsAFinishedLessonAndTheTeacherSeesTheCount() throws Exception {
        String courseId = createCourse("Forja");
        String moduleId = createModule(courseId, "Fundamentos");
        String lessonId = createMarkdown(moduleId, "O que é");
        String otherModule = createModule(courseId, "Outro");
        String otherLesson = createMarkdown(otherModule, "Depois");
        String cohortId = openCohort("Manhã", courseId, "docente");
        enroll(cohortId, "aluno");

        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + lessonId + "/completion")
                        .with(as("docente", "docente")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + otherLesson + "/completion")
                        .with(as("aluno", "colega")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + lessonId + "/completion")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + lessonId + "/completion")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/cohorts/" + cohortId).with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.course.modules[0].lessons[0].completed").value(true))
                .andExpect(jsonPath("$.grade").doesNotExist());

        mvc.perform(get("/cohorts/" + cohortId).with(as("docente", "docente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[0].student").value("aluno"))
                .andExpect(jsonPath("$.students[0].completed").value(1))
                .andExpect(jsonPath("$.students[0].total").value(2))
                .andExpect(jsonPath("$.grade").doesNotExist());
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

    @Test
    void aPracticeGoesFromTheTeacherToTheStudentAndBack() throws Exception {
        String courseId = createCourse("Forja");
        String morning = openCohort("Manhã", courseId, "docente");
        enroll(morning, "aluno");

        mvc.perform(post("/cohorts/" + morning + "/practices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"prompt\":\"Faça\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank-practice"));

        mvc.perform(post("/cohorts/" + morning + "/practices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Oficina\",\"prompt\":\"Mostre a peça.\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isForbidden());

        String practice = openPractice(morning);

        mvc.perform(post("/cohorts/" + morning + "/practices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Oficina\",\"prompt\":\"Outra.\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate-practice"));

        mvc.perform(post("/cohorts/" + morning + "/practices/" + practice + "/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"A peça está no torno.\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated());

        mvc.perform(get("/cohorts/" + morning).with(as("aluno", "colega")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/cohorts/" + morning).with(as("aluno", "aluno")))
                .andExpect(jsonPath("$.practices[0].title").value("Oficina"))
                .andExpect(jsonPath("$.practices[0].deliveries[0].text").value("A peça está no torno."))
                .andExpect(jsonPath("$.practices[0].deliveries[0].feedback").doesNotExist());

        mvc.perform(post("/cohorts/" + morning + "/practices/" + practice + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"aluno\",\"text\":\"Falta o acabamento.\",\"grade\":8}")
                        .with(as("docente", "docente")))
                .andExpect(status().isNoContent());

        mvc.perform(post("/cohorts/" + morning + "/practices/" + practice + "/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Agora com acabamento.\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("already-returned"));

        mvc.perform(get("/cohorts/" + morning).with(as("docente", "docente")))
                .andExpect(jsonPath("$.practices[0].deliveries[0].feedback").value("Falta o acabamento."))
                .andExpect(jsonPath("$.practices[0].deliveries[0].grade").value(8));

        mvc.perform(get("/cohorts/" + morning).with(as("aluno", "aluno")))
                .andExpect(jsonPath("$.practices[0].deliveries.length()").value(1))
                .andExpect(jsonPath("$.practices[0].deliveries[0].feedback").value("Falta o acabamento."))
                .andExpect(jsonPath("$.practices[0].deliveries[0].grade").value(8));
    }

    @Test
    void theTeacherPostsALessonAndTheClassTalksOnIt() throws Exception {
        String courseId = createCourse("Forja");
        String morning = openCohort("Manhã", courseId, "docente");
        enroll(morning, "aluno");
        String moduleId = createModule(courseId, "Bancada");

        mvc.perform(post("/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Do docente\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());

        String lessonId = createMarkdown(moduleId, "Vídeo da grade");

        mvc.perform(get("/courses/curriculum").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Forja"));

        mvc.perform(get("/courses/curriculum").with(as("aluno", "colega")))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/lessons/" + lessonId + "/doubts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dúvida\",\"body\":\"Qual broca?\"}")
                        .with(as("aluno", "colega")))
                .andExpect(status().isNotFound());

        MvcResult doubt = mvc.perform(post("/lessons/" + lessonId + "/doubts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dúvida\",\"body\":\"Qual broca?\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated())
                .andReturn();
        String doubtId = JsonPath.read(doubt.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/lessons/" + lessonId + "/doubts/" + doubtId + "/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"A de 6 mm.\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());

        mvc.perform(get("/lessons/" + lessonId + "/doubts").with(as("aluno", "aluno")))
                .andExpect(jsonPath("$[0].title").value("Dúvida"))
                .andExpect(jsonPath("$[0].replies[0].body").value("A de 6 mm."));

        mvc.perform(get("/grades").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cohortName").value("Manhã"));
    }

    @Test
    void theTeacherPublishesAnExamAndTheGradeLandsOnTheReport() throws Exception {
        String courseId = createCourse("Forja");
        String morning = openCohort("Manhã", courseId, "docente");
        enroll(morning, "aluno");

        mvc.perform(post("/cohorts/" + morning + "/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson("Prova 1"))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isForbidden());

        String examId = openExam(morning);
        String questionId = JsonPath.read(
                mvc.perform(get("/cohorts/" + morning).with(as("docente", "docente")))
                        .andExpect(jsonPath("$.exams[0].questions[0].choices[0].correct").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.exams[0].questions[0].id");
        Object right = JsonPath.read(
                mvc.perform(get("/cohorts/" + morning).with(as("docente", "docente")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.exams[0].questions[0].choices[?(@.correct == true)].id");
        Object wrong = JsonPath.read(
                mvc.perform(get("/cohorts/" + morning).with(as("aluno", "aluno")))
                        .andExpect(jsonPath("$.exams[0].questions[0].choices[0].correct").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.exams[0].questions[0].choices[?(@.label == 'Errada')].id");

        mvc.perform(post("/cohorts/" + morning + "/exams/" + examId + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(questionId, first(wrong)))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated());

        mvc.perform(post("/cohorts/" + morning + "/exams/" + examId + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(questionId, first(right)))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("already-taken"));

        mvc.perform(get("/grades").with(as("docente", "docente")))
                .andExpect(jsonPath("$[0].exams[0].title").value("Prova 1"))
                .andExpect(jsonPath("$[0].exams[0].scores[0].student").value("aluno"))
                .andExpect(jsonPath("$[0].exams[0].scores[0].grade").value(0));

        mvc.perform(get("/cohorts/" + morning).with(as("aluno", "colega")))
                .andExpect(status().isNotFound());
    }

    private String openExam(String cohortId) throws Exception {
        MvcResult result = mvc.perform(post("/cohorts/" + cohortId + "/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson("Prova 1"))
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String examJson(String title) {
        return "{\"title\":\"" + title + "\",\"questions\":[{\"prompt\":\"Qual?\","
                + "\"choices\":[{\"label\":\"Certa\",\"correct\":true},{\"label\":\"Errada\",\"correct\":false}]}]}";
    }

    private static String attemptJson(String questionId, String choiceId) {
        return "{\"answers\":[{\"questionId\":\"" + questionId + "\",\"choiceId\":\"" + choiceId + "\"}]}";
    }

    private static String first(Object value) {
        if (value instanceof java.util.List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    private String openPractice(String cohortId) throws Exception {
        MvcResult result = mvc.perform(post("/cohorts/" + cohortId + "/practices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Oficina\",\"prompt\":\"Mostre a peça.\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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

    private String createMarkdown(String moduleId, String title) throws Exception {
        MvcResult result = mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .param("title", title)
                        .param("kind", "markdown")
                        .param("markdown", "Oficina")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
