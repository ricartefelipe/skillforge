package skillforge;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        "skillforge.issuer=http://127.0.0.1:1/realms/skillforge",
        "skillforge.identity-timeout=500ms"
})
@AutoConfigureMockMvc
class ScormTicketAccessTest {

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
                mail_outbox, account_mail, scorm_item_track, scorm_item, scorm_track, scorm_ticket, scorm_file,
                lesson_tool, lti_hint, lti_key,
                peer_review, study_member, study_group, exam_clock, sitting, attempt_answer, attempt,
                choice, question, exam, bank_choice, bank_question, lesson_asset, reply, doubt, submission,
                practice, completion, enrollment, cohort, lesson, module, course
                """);
    }

    @Test
    void aTicketLetsThePackagePageFetchItsFilesWithoutASession() throws Exception {
        String lessonId = createScorm();

        String ticket = JsonPath.read(mvc.perform(post("/lessons/" + lessonId + "/scorm/ticket").with(as("aluno")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.ticket");

        mvc.perform(get("/lessons/" + lessonId + "/scorm/files/" + ticket).param("path", "passo.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string("<html></html>"));
    }

    @Test
    void issuingATicketNeedsAnAuthenticatedActor() throws Exception {
        String lessonId = createScorm();

        mvc.perform(post("/lessons/" + lessonId + "/scorm/ticket")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTicketIsScopedToItsOwnLessonAndRefusesTraversal() throws Exception {
        String lessonId = createScorm();
        String other = createScorm();
        String ticket = JsonPath.read(mvc.perform(post("/lessons/" + lessonId + "/scorm/ticket").with(as("aluno")))
                .andReturn().getResponse().getContentAsString(), "$.ticket");

        mvc.perform(get("/lessons/" + other + "/scorm/files/" + ticket).param("path", "passo.html"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/lessons/" + lessonId + "/scorm/files/" + UUID.randomUUID()).param("path", "passo.html"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/lessons/" + lessonId + "/scorm/files/" + ticket).param("path", "../passo.html"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void anExpiredTicketIsRefused() throws Exception {
        String lessonId = createScorm();
        String ticket = JsonPath.read(mvc.perform(post("/lessons/" + lessonId + "/scorm/ticket").with(as("aluno")))
                .andReturn().getResponse().getContentAsString(), "$.ticket");
        jdbc.update("UPDATE scorm_ticket SET expires_at = now() - interval '1 second' WHERE token = ?::uuid", ticket);

        mvc.perform(get("/lessons/" + lessonId + "/scorm/files/" + ticket).param("path", "passo.html"))
                .andExpect(status().isNotFound());
    }

    private String createScorm() throws Exception {
        String courseId = createCourse("Pacote " + UUID.randomUUID());
        String moduleId = createModule(courseId, "Conteúdo");
        return createFile(moduleId, "Aula SCORM", "scorm", "curso.zip", "application/zip", scormZip());
    }

    private static byte[] scormZip() throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("imsmanifest.xml"));
            zip.write("""
                    <manifest><resources><resource identifier="p" href="passo.html"/></resources>
                    <item identifierref="p"><title>Passo</title></item></manifest>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("passo.html"));
            zip.write("<html></html>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
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
