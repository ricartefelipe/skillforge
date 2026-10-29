package skillforge;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.mock.web.MockMultipartFile;
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
                TRUNCATE certificate, criterion_mark, criterion, presence, class_day, announcement, inbox,
                mail_outbox, account_mail, scorm_item_track, scorm_item, scorm_track, scorm_ticket, scorm_file,
                lesson_tool, lti_hint, lti_key,
                peer_review, study_member, study_group, exam_clock, sitting, attempt_answer, attempt,
                choice, question, exam, bank_choice, bank_question, lesson_asset, reply, doubt, submission,
                practice, completion, enrollment, cohort, lesson, module, course
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
    void theStudentFinishesTheLessonsBeforeTheExam() throws Exception {
        String courseId = createCourse("Oficina");
        String moduleId = createModule(courseId, "Fundamentos");
        String lessonId = createMarkdown(moduleId, "O ofício");
        String cohortId = openCohort("Tarde", courseId, "docente");
        enroll(cohortId, "aluno");
        String examId = openExam(cohortId);

        mvc.perform(post("/cohorts/" + cohortId + "/exams/" + examId + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("lessons"));

        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + lessonId + "/completion")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());

        String page = mvc.perform(get("/cohorts/" + cohortId).with(as("docente", "docente")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String questionId = JsonPath.read(page, "$.exams[0].questions[0].id");
        String choiceId = first(JsonPath.read(page, "$.exams[0].questions[0].choices[0].id"));
        mvc.perform(post("/cohorts/" + cohortId + "/exams/" + examId + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(questionId, choiceId))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated());
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

    @Test
    void aClassKeepsDeadlineRubricRollNoticeAndCertificate() throws Exception {
        String courseId = createCourse("Oficina");
        String morning = openCohort("Noite", courseId, "docente");
        enroll(morning, "aluno");

        mvc.perform(post("/cohorts/" + morning + "/practices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Atrasada\",\"prompt\":\"Tarde.\",\"dueAt\":\"2020-01-01T00:00:00Z\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());

        String late = JsonPath.read(mvc.perform(get("/cohorts/" + morning).with(as("docente", "docente")))
                .andReturn().getResponse().getContentAsString(), "$.practices[0].id");

        mvc.perform(post("/cohorts/" + morning + "/practices/" + late + "/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Cheguei.\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("past-due"));

        mvc.perform(get("/cohorts/" + morning + "/certificate").with(as("aluno", "aluno")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("not-certified"));

        mvc.perform(post("/cohorts/" + morning + "/practices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Rubrica\",\"prompt\":\"Mostre.\",\"criteria\":["
                                + "{\"name\":\"Forma\",\"points\":6},{\"name\":\"Acabamento\",\"points\":4}]}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/cohorts/" + morning).with(as("docente", "docente")))
                .andReturn().getResponse().getContentAsString();
        String practice = only(JsonPath.read(body, "$.practices[?(@.title == 'Rubrica')].id"));
        String forma = only(JsonPath.read(
                body,
                "$.practices[?(@.title == 'Rubrica')].criteria[?(@.name == 'Forma')].id"));
        String acabamento = only(JsonPath.read(
                body,
                "$.practices[?(@.title == 'Rubrica')].criteria[?(@.name == 'Acabamento')].id"));

        mvc.perform(post("/cohorts/" + morning + "/practices/" + practice + "/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"A peça.\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated());

        mvc.perform(post("/cohorts/" + morning + "/practices/" + practice + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"aluno\",\"text\":\"Boa.\",\"grade\":8,\"marks\":["
                                + "{\"criterionId\":\"" + forma + "\",\"points\":5},"
                                + "{\"criterionId\":\"" + acabamento + "\",\"points\":3}]}")
                        .with(as("docente", "docente")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/cohorts/" + morning).with(as("aluno", "aluno")))
                .andExpect(jsonPath("$.practices[?(@.title == 'Rubrica')].deliveries[0].grade").value(8));

        mvc.perform(post("/cohorts/" + morning + "/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Amanhã\",\"body\":\"Tragam a peça.\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());

        mvc.perform(get("/cohorts/" + morning + "/announcements").with(as("aluno", "aluno")))
                .andExpect(jsonPath("$[0].title").value("Amanhã"));

        mvc.perform(get("/cohorts/" + morning + "/announcements").with(as("aluno", "colega")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/cohorts/" + morning + "/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heldOn\":\"2026-10-14\",\"marks\":[{\"student\":\"aluno\",\"present\":true}]}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());

        mvc.perform(post("/cohorts/" + morning + "/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heldOn\":\"2026-10-14\",\"marks\":[{\"student\":\"aluno\",\"present\":false}]}")
                        .with(as("docente", "docente")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate-day"));

        mvc.perform(get("/cohorts/" + morning + "/certificate").with(as("docente", "docente")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/cohorts/" + morning + "/certificate").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value("8.0"))
                .andExpect(jsonPath("$.courseName").value("Oficina"));
        mvc.perform(get("/grades").with(as("aluno", "aluno")))
                .andExpect(jsonPath("$[0].average").value("8.0"))
                .andExpect(jsonPath("$[0].certified").value(true));
    }

    @Test
    void theStudioCoversResumeBankGroupsAndFiles() throws Exception {
        String courseId = createCourse("Oficina completa");
        String moduleId = createModule(courseId, "Unidade");
        String lessonId = createMarkdown(moduleId, "Primeira");
        String cohortId = openCohort("Integral", courseId, "docente");
        enroll(cohortId, "aluno");
        enroll(cohortId, "colega");

        mvc.perform(get("/resume").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonId));
        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + lessonId + "/completion").with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/resume").with(as("aluno", "aluno"))).andExpect(status().isNoContent());

        mvc.perform(put("/lessons/" + lessonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Primeira aula\",\"markdown\":\"Texto novo\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isNoContent());
        mvc.perform(multipart("/lessons/" + lessonId + "/assets")
                        .file(new MockMultipartFile("file", "nota.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("name", "Nota")
                        .param("kind", "ebook")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());
        mvc.perform(get("/lessons/" + lessonId + "/assets").with(as("aluno", "aluno")))
                .andExpect(jsonPath("$[0].name").value("Nota"));

        String bankId = JsonPath.read(mvc.perform(post("/courses/" + courseId + "/bank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examQuestion())
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.id");
        mvc.perform(post("/cohorts/" + cohortId + "/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tempo\",\"minutes\":10,\"attemptsAllowed\":2,\"bank\":[\""
                                + bankId + "\"],\"questions\":[]}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());
        String page = mvc.perform(get("/cohorts/" + cohortId).with(as("docente", "docente")))
                .andExpect(jsonPath("$.exams[0].attemptsAllowed").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String examId = JsonPath.read(page, "$.exams[0].id");
        String questionId = JsonPath.read(page, "$.exams[0].questions[0].id");
        String choiceId = first(JsonPath.read(page, "$.exams[0].questions[0].choices[?(@.correct == true)].id"));
        mvc.perform(post("/cohorts/" + cohortId + "/exams/" + examId + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(questionId, choiceId))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("not-started"));
        mvc.perform(post("/cohorts/" + cohortId + "/exams/" + examId + "/start").with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/cohorts/" + cohortId + "/exams/" + examId + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(questionId, choiceId))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated());

        String groupId = JsonPath.read(mvc.perform(post("/cohorts/" + cohortId + "/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mesa\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.id");
        mvc.perform(post("/cohorts/" + cohortId + "/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"aluno\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());
        mvc.perform(post("/cohorts/" + cohortId + "/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":\"colega\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());
        String practiceId = openPractice(cohortId);
        mvc.perform(post("/cohorts/" + cohortId + "/practices/" + practiceId + "/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"A peça.\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated());
        mvc.perform(post("/cohorts/" + cohortId + "/practices/" + practiceId + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"aluno\",\"comment\":\"Boa peça.\",\"mark\":8}")
                        .with(as("aluno", "colega")))
                .andExpect(status().isCreated());

        mvc.perform(post("/cohorts/" + cohortId + "/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Amanhã\",\"body\":\"Tragam a peça.\"}")
                        .with(as("docente", "docente")))
                .andExpect(status().isCreated());
        mvc.perform(get("/inbox").with(as("aluno", "aluno")))
                .andExpect(jsonPath("$[?(@.body == 'Novo aviso da turma.')]").isNotEmpty());
        mvc.perform(put("/cohorts/" + cohortId + "/weights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practiceWeight\":2,\"examWeight\":1}")
                        .with(as("docente", "docente")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/grades").with(as("aluno", "aluno")))
                .andExpect(jsonPath("$[0].practiceWeight").value(2))
                .andExpect(jsonPath("$[0].examWeight").value(1));
    }

    @Test
    void resumeStaysInTheCourseAlreadyStarted() throws Exception {
        String waiting = createCourse("Alfa");
        String waitingModule = createModule(waiting, "Unidade");
        createMarkdown(waitingModule, "Ainda não");
        String started = createCourse("Beta");
        String startedModule = createModule(started, "Unidade");
        String done = createMarkdown(startedModule, "Feita");
        String next = createMarkdown(startedModule, "Seguinte");
        String waitingCohort = openCohort("Turma alfa", waiting, "docente");
        String startedCohort = openCohort("Turma beta", started, "docente");
        enroll(waitingCohort, "aluno");
        enroll(startedCohort, "aluno");

        mvc.perform(post("/cohorts/" + startedCohort + "/lessons/" + done + "/completion").with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/resume").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(next));
    }

    @Test
    void searchPackageToolAndQuestionTextStayAfterAnAttempt() throws Exception {
        String courseId = createCourse("Acervo");
        String moduleId = createModule(courseId, "Unidade");
        String lessonId = createMarkdown(moduleId, "Primeira peça");
        String cohortId = openCohort("Integral", courseId, "docente");
        enroll(cohortId, "aluno");

        mvc.perform(get("/search").param("q", "peça").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("aula"))
                .andExpect(jsonPath("$[0].title").value("Primeira peça"));

        mvc.perform(put("/courses/" + courseId + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[{\"id\":\"" + moduleId + "\",\"lessons\":[\"" + lessonId + "\"]}]}")
                        .with(as("docente", "docente")))
                .andExpect(status().isNoContent());

        mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .file(new MockMultipartFile("file", "curso.zip", "application/zip", scormZip()))
                        .param("title", "Pacote")
                        .param("kind", "scorm")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated());

        mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .param("title", "Ferramenta")
                        .param("kind", "lti")
                        .param("launchUrl", "https://tool.example/launch")
                        .param("secret", "segredo-longo")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated());
        String toolId = first(JsonPath.read(
                mvc.perform(get("/courses/curriculum").with(as("administracao", "administracao")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[0].modules[0].lessons[?(@.title == 'Ferramenta')].id"));
        String launch = mvc.perform(get("/lessons/" + toolId + "/launch").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://tool.example/launch"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(launch.contains("segredo-longo"));

        mvc.perform(put("/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"aluno@example.com\"}")
                        .with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/me/email").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("aluno@example.com"));

        String examId = openExam(cohortId);
        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + lessonId + "/completion").with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + toolId + "/completion").with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());
        String packId = first(JsonPath.read(
                mvc.perform(get("/courses/curriculum").with(as("administracao", "administracao")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[0].modules[0].lessons[?(@.title == 'Pacote')].id"));
        mvc.perform(post("/cohorts/" + cohortId + "/lessons/" + packId + "/completion").with(as("aluno", "aluno")))
                .andExpect(status().isNoContent());
        String page = mvc.perform(get("/cohorts/" + cohortId).with(as("docente", "docente")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String questionId = JsonPath.read(page, "$.exams[0].questions[0].id");
        String choiceId = first(JsonPath.read(page, "$.exams[0].questions[0].choices[0].id"));
        mvc.perform(post("/cohorts/" + cohortId + "/exams/" + examId + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson(questionId, choiceId))
                        .with(as("aluno", "aluno")))
                .andExpect(status().isCreated());
        mvc.perform(put("/cohorts/" + cohortId + "/exams/" + examId + "/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\":[{\"id\":\"" + questionId + "\",\"prompt\":\"Novo enunciado\","
                                + "\"choices\":[{\"id\":\"" + choiceId + "\",\"label\":\"Nova\"}]}]}")
                        .with(as("docente", "docente")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/cohorts/" + cohortId).with(as("aluno", "aluno")))
                .andExpect(jsonPath("$.exams[0].questions[0].prompt").value("Novo enunciado"))
                .andExpect(jsonPath("$.exams[0].scores[0].grade").exists());
    }

    @Test
    void theReportNeverRevealsTheAnswerKeyToTheStudent() throws Exception {
        String courseId = createCourse("Forja");
        String morning = openCohort("Manhã", courseId, "docente");
        enroll(morning, "aluno");
        openExam(morning);

        mvc.perform(get("/grades").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exams[0].questions[0].choices[0].label").value("Certa"))
                .andExpect(jsonPath("$[0].exams[0].questions[0].choices[0].correct").doesNotExist())
                .andExpect(jsonPath("$[0].exams[0].questions[0].choices[1].correct").doesNotExist());

        mvc.perform(get("/grades").with(as("docente", "docente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exams[0].questions[0].choices[0].label").value("Certa"))
                .andExpect(jsonPath("$[0].exams[0].questions[0].choices[0].correct").value(true));
    }

    @Test
    void aModernLtiLaunchOnlyPostsToTheRegisteredTool() throws Exception {
        String courseId = createCourse("Forja");
        String moduleId = createModule(courseId, "Bancada");
        mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .param("title", "Ferramenta")
                        .param("kind", "lti")
                        .param("launchUrl", "https://tool.example/launch")
                        .param("secret", "segredo-longo")
                        .param("authLogin", "https://tool.example/login")
                        .with(as("administracao", "administracao")))
                .andExpect(status().isCreated());
        String toolId = first(JsonPath.read(
                mvc.perform(get("/courses/curriculum").with(as("administracao", "administracao")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[0].modules[0].lessons[?(@.title == 'Ferramenta')].id"));

        String target = "https://tool.example/launch";
        String body = mvc.perform(get("/lti/auth")
                        .param("lti_message_hint", launchHint(toolId))
                        .param("target_link_uri", target))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("id_token"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains(target));

        mvc.perform(get("/lti/auth")
                        .param("lti_message_hint", launchHint(toolId))
                        .param("target_link_uri", "https://evil.example/collect"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("launch"));
    }

    private String launchHint(String toolId) throws Exception {
        String launch = mvc.perform(get("/lessons/" + toolId + "/launch").with(as("aluno", "aluno")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(launch, "$.fields.lti_message_hint");
    }

    private static byte[] scormZip() throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer);
        zip.putNextEntry(new java.util.zip.ZipEntry("imsmanifest.xml"));
        String manifest = """
                <manifest><resources><resource href="index.html"/></resources></manifest>
                """;
        zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new java.util.zip.ZipEntry("index.html"));
        zip.write("<html></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.close();
        return buffer.toByteArray();
    }

    private static String examQuestion() {
        return "{\"prompt\":\"Qual?\","
                + "\"choices\":[{\"label\":\"Certa\",\"correct\":true},{\"label\":\"Errada\",\"correct\":false}]}";
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

    private static String only(Object value) {
        Object current = value;
        while (current instanceof java.util.List<?> list) {
            if (list.isEmpty()) {
                return "";
            }
            current = list.get(0);
        }
        return String.valueOf(current);
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
