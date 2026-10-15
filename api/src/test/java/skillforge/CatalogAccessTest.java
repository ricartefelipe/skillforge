package skillforge;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
        "skillforge.catalog.markdown-max=12",
        "skillforge.catalog.pdf-max=32B",
        "skillforge.catalog.video-max=32B",
        "skillforge.issuer=http://127.0.0.1:1/realms/skillforge",
        "skillforge.identity-timeout=500ms"
})
@AutoConfigureMockMvc
class CatalogAccessTest {

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
    void emptyCatalog() throws Exception {
        mvc.perform(get("/courses").with(as("administracao"))).andExpect(status().isOk());
        jdbc.update("""
                TRUNCATE certificate, criterion_mark, criterion, presence, class_day, announcement, inbox,
                mail_outbox, account_mail, scorm_item_track, scorm_item, scorm_track, scorm_file,
                lesson_tool, lti_hint, lti_key,
                peer_review, study_member, study_group, exam_clock, sitting, attempt_answer, attempt,
                choice, question, exam, bank_choice, bank_question, lesson_asset, reply, doubt, submission,
                practice, completion, enrollment, cohort, lesson, module, course
                """);
    }

    @Test
    void anonymousCallerCannotReadTheCatalog() throws Exception {
        mvc.perform(get("/courses")).andExpect(status().isUnauthorized());
    }

    @Test
    void aStudentCannotComposeAndCanRead() throws Exception {
        mvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forja\"}")
                        .with(as("aluno")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));

        mvc.perform(get("/courses").with(as("aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aTeacherCannotCompose() throws Exception {
        mvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forja\"}")
                        .with(as("docente")))
                .andExpect(status().isForbidden());
    }

    @Test
    void severalRolesCannotReadTheCatalog() throws Exception {
        mvc.perform(get("/courses").with(jwt().jwt(token -> token
                        .claim("realm_access", Map.of("roles", List.of("aluno", "docente"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrationBuildsACourseTheOthersCanRead() throws Exception {
        String courseId = createCourse("Forja");
        String moduleId = createModule(courseId, "Fundamentos");
        String lessonId = createMarkdown(moduleId, "O que é", "Oficina");

        mvc.perform(get("/courses").with(as("docente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Forja"))
                .andExpect(jsonPath("$[0].modules[0].name").value("Fundamentos"))
                .andExpect(jsonPath("$[0].modules[0].lessons[0].title").value("O que é"))
                .andExpect(jsonPath("$[0].modules[0].lessons[0].kind").value("markdown"));

        mvc.perform(get("/lessons/" + lessonId).with(as("aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value("Oficina"))
                .andExpect(jsonPath("$.kind").value("markdown"));

        mvc.perform(get("/lessons/" + lessonId + "/media").with(as("aluno")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBlankOrRepeatedCourseNameIsRejected() throws Exception {
        mvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}")
                        .with(as("administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank-course"));

        createCourse("Forja");
        mvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"forja\"}")
                        .with(as("administracao")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate-course"));
    }

    @Test
    void aModuleNeedsACourseThatExists() throws Exception {
        mvc.perform(post("/courses/00000000-0000-0000-0000-000000000000/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fundamentos\"}")
                        .with(as("administracao")))
                .andExpect(status().isNotFound());

        String courseId = createCourse("Forja");
        mvc.perform(post("/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}")
                        .with(as("administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank-module"));
    }

    @Test
    void pdfAndVideoStayFilesAndAFakePdfIsRefused() throws Exception {
        String courseId = createCourse("Forja");
        String moduleId = createModule(courseId, "Fundamentos");
        byte[] pdf = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        String pdfId = createFile(moduleId, "Notas", "pdf", "notas.pdf", "application/pdf", pdf);

        mvc.perform(get("/lessons/" + pdfId + "/media").with(as("aluno")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf));

        mvc.perform(get("/lessons/" + pdfId).with(as("aluno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").doesNotExist());

        byte[] mp4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'};
        String videoId = createFile(moduleId, "Demonstração", "video", "demo.mp4", "video/mp4", mp4);
        mvc.perform(get("/lessons/" + videoId + "/media").with(as("docente")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(content().bytes(mp4));

        mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .file(new MockMultipartFile("file", "falso.pdf", "application/pdf", "hello".getBytes()))
                        .param("title", "Falso")
                        .param("kind", "pdf")
                        .with(as("administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("not-pdf"));

        byte[] huge = new byte[33];
        huge[0] = '%';
        huge[1] = 'P';
        huge[2] = 'D';
        huge[3] = 'F';
        mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .file(new MockMultipartFile("file", "grande.pdf", "application/pdf", huge))
                        .param("title", "Grande")
                        .param("kind", "pdf")
                        .with(as("administracao")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("too-large"));
    }

    private String createCourse(String name) throws Exception {
        MvcResult result = mvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .with(as("administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createModule(String courseId, String name) throws Exception {
        MvcResult result = mvc.perform(post("/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .with(as("administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createMarkdown(String moduleId, String title, String markdown) throws Exception {
        MvcResult result = mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .param("title", title)
                        .param("kind", "markdown")
                        .param("markdown", markdown)
                        .with(as("administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createFile(
            String moduleId,
            String title,
            String kind,
            String filename,
            String contentType,
            byte[] bytes) throws Exception {
        MvcResult result = mvc.perform(multipart("/modules/" + moduleId + "/lessons")
                        .file(new MockMultipartFile("file", filename, contentType, bytes))
                        .param("title", title)
                        .param("kind", kind)
                        .with(as("administracao")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.claim("realm_access", Map.of("roles", List.of(role))));
    }

}
