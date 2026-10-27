package skillforge.interfaces.http;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonInclude;

import skillforge.application.catalog.CatalogApplication;
import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.Lesson;
import skillforge.domain.catalog.LessonAsset;
import skillforge.domain.catalog.LessonFile;
import skillforge.domain.catalog.LessonText;
import skillforge.domain.catalog.ModuleOrder;
import skillforge.domain.catalog.Module;

@RestController
class CatalogController {

    private final CatalogApplication catalog;

    CatalogController(CatalogApplication catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/courses")
    List<CourseBody> courses(Authentication authentication) {
        return catalog.list(RequestActor.from(authentication)).stream().map(CourseBody::from).toList();
    }

    @GetMapping("/courses/curriculum")
    List<CourseBody> curriculum(Authentication authentication) {
        return catalog.curriculum(RequestActor.from(authentication)).stream().map(CourseBody::from).toList();
    }

    @GetMapping("/courses/teaching")
    List<UUID> teaching(Authentication authentication) {
        return catalog.teaching(RequestActor.from(authentication)).stream().toList();
    }

    @PostMapping("/courses")
    ResponseEntity<IdName> createCourse(Authentication authentication, @RequestBody NameBody body) {
        CatalogApplication.Created created = catalog.createCourse(
                RequestActor.from(authentication),
                body == null ? null : body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(created.id(), created.name()));
    }

    @PostMapping("/courses/{courseId}/modules")
    ResponseEntity<IdName> createModule(
            Authentication authentication,
            @PathVariable UUID courseId,
            @RequestBody NameBody body) {
        CatalogApplication.Created created = catalog.createModule(
                RequestActor.from(authentication),
                courseId,
                body == null ? null : body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(created.id(), created.name()));
    }

    @PostMapping(path = "/modules/{moduleId}/lessons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<LessonBody> createLesson(
            Authentication authentication,
            @PathVariable UUID moduleId,
            @RequestParam String title,
            @RequestParam String kind,
            @RequestParam(required = false) String markdown,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String launchUrl,
            @RequestParam(required = false) String secret,
            @RequestParam(required = false) String authLogin) throws IOException {
        byte[] media = file == null || file.isEmpty() ? null : file.getBytes();
        Lesson lesson = catalog.createLesson(
                RequestActor.from(authentication),
                moduleId,
                title,
                kind,
                markdown,
                media,
                launchUrl,
                secret,
                authLogin);
        return ResponseEntity.status(HttpStatus.CREATED).body(LessonBody.from(lesson));
    }

    @PutMapping("/lessons/{id}")
    ResponseEntity<Void> reviseLesson(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody LessonEdit body) {
        LessonEdit request = body == null ? new LessonEdit(null, null) : body;
        catalog.reviseLesson(RequestActor.from(authentication), id, request.title(), request.markdown());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/lessons/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<LessonAsset> addAsset(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam String name,
            @RequestParam String kind,
            @RequestParam MultipartFile file) throws IOException {
        LessonAsset asset = catalog.addAsset(
                RequestActor.from(authentication),
                id,
                name,
                kind,
                file == null ? null : file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(asset);
    }

    @GetMapping("/lessons/{id}/assets")
    List<LessonAsset> assets(Authentication authentication, @PathVariable UUID id) {
        return catalog.assets(RequestActor.from(authentication), id);
    }

    @GetMapping("/lessons/{id}/assets/{assetId}")
    ResponseEntity<byte[]> asset(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID assetId,
            @RequestHeader HttpHeaders headers) {
        return serve(catalog.asset(RequestActor.from(authentication), id, assetId), headers);
    }

    @DeleteMapping("/lessons/{id}/assets/{assetId}")
    ResponseEntity<Void> removeAsset(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID assetId) {
        catalog.removeAsset(RequestActor.from(authentication), id, assetId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/lessons/{id}")
    ResponseEntity<Void> removeLesson(Authentication authentication, @PathVariable UUID id) {
        catalog.removeLesson(RequestActor.from(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lessons/{id}")
    LessonTextBody lesson(Authentication authentication, @PathVariable UUID id) {
        return LessonTextBody.from(catalog.lesson(RequestActor.from(authentication), id));
    }

    @GetMapping("/lessons/{id}/scorm")
    skillforge.domain.catalog.ScormOutline scorm(Authentication authentication, @PathVariable UUID id) {
        return catalog.scormLaunch(RequestActor.from(authentication), id);
    }

    @GetMapping("/lessons/{id}/scorm/file")
    ResponseEntity<byte[]> scormFile(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam String path) {
        LessonFile file = catalog.scormFile(RequestActor.from(authentication), id, path);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType())).body(file.content());
    }

    @PostMapping("/lessons/{id}/scorm/commit")
    ResponseEntity<Void> track(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody TrackBody body) {
        TrackBody request = body == null ? new TrackBody(null, null, null) : body;
        catalog.trackScorm(
                RequestActor.from(authentication),
                id,
                request.status(),
                request.score(),
                request.position() == null ? 1 : request.position());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lessons/{id}/launch")
    skillforge.domain.catalog.SignedLaunch launch(
            Authentication authentication,
            @PathVariable UUID id,
            jakarta.servlet.http.HttpServletRequest request) {
        String iss = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            iss = iss + ":" + request.getServerPort();
        }
        return catalog.launch(RequestActor.from(authentication), id, iss);
    }

    @GetMapping(value = "/lti/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    String jwks() {
        return catalog.jwks();
    }

    @GetMapping(value = "/lti/auth", produces = MediaType.TEXT_HTML_VALUE)
    String authorize(
            @RequestParam java.util.UUID lti_message_hint,
            @RequestParam String target_link_uri,
            jakarta.servlet.http.HttpServletRequest request) {
        String iss = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            iss = iss + ":" + request.getServerPort();
        }
        return catalog.authorize(lti_message_hint, target_link_uri, iss);
    }

    @PutMapping("/courses/{courseId}/order")
    ResponseEntity<Void> reorder(
            Authentication authentication,
            @PathVariable UUID courseId,
            @RequestBody OrderBody body) {
        java.util.List<ModuleOrder> modules = new java.util.ArrayList<>();
        if (body != null && body.modules() != null) {
            for (ModuleOrderBody module : body.modules()) {
                modules.add(new ModuleOrder(
                        module.id(),
                        module.lessons() == null ? java.util.List.of() : module.lessons()));
            }
        }
        catalog.reorder(RequestActor.from(authentication), courseId, modules);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lessons/{id}/media")
    ResponseEntity<byte[]> media(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader HttpHeaders headers) {
        return serve(catalog.media(RequestActor.from(authentication), id), headers);
    }

    // Serves a file whole, or just the asked byte range, so a player can seek without downloading it all.
    private static ResponseEntity<byte[]> serve(LessonFile file, HttpHeaders headers) {
        byte[] bytes = file.content();
        MediaType type = MediaType.parseMediaType(file.mediaType());
        List<HttpRange> ranges = headers.getRange();
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(type)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(bytes.length)
                    .body(bytes);
        }
        long length = bytes.length;
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        if (start >= length || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
        int from = (int) start;
        int to = (int) Math.min(end, length - 1);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(type)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + from + "-" + to + "/" + length)
                .contentLength(to - from + 1)
                .body(java.util.Arrays.copyOfRange(bytes, from, to + 1));
    }

    record LessonEdit(String title, String markdown) {
    }

    record LaunchPath(String path) {
    }

    record TrackBody(String status, Integer score, Integer position) {
    }

    record OrderBody(java.util.List<ModuleOrderBody> modules) {
    }

    record ModuleOrderBody(UUID id, java.util.List<UUID> lessons) {
    }

    record NameBody(String name) {
    }

    record IdName(UUID id, String name) {
    }

    record LessonBody(UUID id, String title, String kind) {

        static LessonBody from(Lesson lesson) {
            return new LessonBody(lesson.id(), lesson.title(), lesson.kind().wire());
        }

    }

    record ModuleBody(UUID id, String name, List<LessonBody> lessons) {

        static ModuleBody from(Module module) {
            return new ModuleBody(
                    module.id(),
                    module.name(),
                    module.lessons().stream().map(LessonBody::from).toList());
        }

    }

    record CourseBody(UUID id, String name, List<ModuleBody> modules) {

        static CourseBody from(Course course) {
            return new CourseBody(
                    course.id(),
                    course.name(),
                    course.modules().stream().map(ModuleBody::from).toList());
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LessonTextBody(UUID id, String title, String kind, String markdown) {

        static LessonTextBody from(LessonText lesson) {
            return new LessonTextBody(lesson.id(), lesson.title(), lesson.kind().wire(), lesson.markdown());
        }

    }

}
