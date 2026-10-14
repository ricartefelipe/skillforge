package skillforge;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class CatalogController {

    private final CatalogStore store;

    private final SkillforgeProperties properties;

    CatalogController(CatalogStore store, SkillforgeProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @GetMapping("/courses")
    List<CourseBody> courses(Authentication authentication) {
        requireRole(authentication);
        return store.courses().stream().map(CourseBody::from).toList();
    }

    @PostMapping("/courses")
    ResponseEntity<IdName> createCourse(Authentication authentication, @RequestBody NameBody body) {
        requireAdministration(authentication);
        String name = Names.required(body == null ? null : body.name(), "blank-course");
        UUID id = store.createCourse(name);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(id, name));
    }

    @PostMapping("/courses/{courseId}/modules")
    ResponseEntity<IdName> createModule(
            Authentication authentication,
            @PathVariable UUID courseId,
            @RequestBody NameBody body) {
        requireAdministration(authentication);
        String name = Names.required(body == null ? null : body.name(), "blank-module");
        UUID id = store.createModule(courseId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(id, name));
    }

    @PostMapping(path = "/modules/{moduleId}/lessons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<LessonSummaryBody> createLesson(
            Authentication authentication,
            @PathVariable UUID moduleId,
            @RequestParam String title,
            @RequestParam String kind,
            @RequestParam(required = false) String markdown,
            @RequestParam(required = false) MultipartFile file) throws IOException {
        requireAdministration(authentication);
        String lessonTitle = Names.required(title, "blank-title");
        byte[] media = file == null || file.isEmpty() ? null : file.getBytes();
        LessonContent.Decision decision = LessonContent.decide(kind, markdown, media, limits());
        if (decision instanceof LessonContent.Rejected rejected) {
            throw new CatalogRejection(rejected.code(), HttpStatus.BAD_REQUEST);
        }
        LessonContent.Accepted accepted = (LessonContent.Accepted) decision;
        UUID id = store.createLesson(moduleId, lessonTitle, accepted);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LessonSummaryBody(id, lessonTitle, accepted.kind()));
    }

    @GetMapping("/lessons/{id}")
    Map<String, Object> lesson(Authentication authentication, @PathVariable UUID id) {
        requireRole(authentication);
        CatalogStore.LessonView view = store.lesson(id)
                .orElseThrow(() -> new CatalogRejection("not-found", HttpStatus.NOT_FOUND));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", view.id());
        body.put("title", view.title());
        body.put("kind", view.kind());
        if (view.markdown() != null) {
            body.put("markdown", view.markdown());
        }
        return body;
    }

    @GetMapping("/lessons/{id}/media")
    ResponseEntity<byte[]> media(Authentication authentication, @PathVariable UUID id) {
        requireRole(authentication);
        CatalogStore.Media media = store.media(id)
                .orElseThrow(() -> new CatalogRejection("not-found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.mediaType()))
                .body(media.bytes());
    }

    private LessonContent.Limits limits() {
        SkillforgeProperties.Catalog catalog = properties.getCatalog();
        return new LessonContent.Limits(
                catalog.getMarkdownMax(),
                catalog.getPdfMax().toBytes(),
                catalog.getVideoMax().toBytes());
    }

    private static void requireAdministration(Authentication authentication) {
        if (!"administracao".equals(requireRole(authentication))) {
            throw new CatalogRejection("forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private static String requireRole(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new CatalogRejection("forbidden", HttpStatus.FORBIDDEN);
        }
        Optional<String> role = InstitutionalRole.only(InstitutionalRole.fromToken(jwt));
        if (role.isEmpty()) {
            throw new CatalogRejection("forbidden", HttpStatus.FORBIDDEN);
        }
        return role.get();
    }

    record NameBody(String name) {
    }

    record IdName(UUID id, String name) {
    }

    record ErrorBody(String error) {
    }

    record LessonSummaryBody(UUID id, String title, String kind) {
    }

    record CourseBody(UUID id, String name, List<ModuleBody> modules) {
        static CourseBody from(CatalogStore.CourseView view) {
            return new CourseBody(
                    view.id(),
                    view.name(),
                    view.modules().stream().map(ModuleBody::from).toList());
        }
    }

    record ModuleBody(UUID id, String name, List<LessonSummaryBody> lessons) {
        static ModuleBody from(CatalogStore.ModuleView view) {
            return new ModuleBody(
                    view.id(),
                    view.name(),
                    view.lessons().stream()
                            .map(lesson -> new LessonSummaryBody(lesson.id(), lesson.title(), lesson.kind()))
                            .toList());
        }
    }

}
