package skillforge.interfaces.http;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonInclude;

import skillforge.application.catalog.CatalogApplication;
import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.Lesson;
import skillforge.domain.catalog.LessonFile;
import skillforge.domain.catalog.LessonText;
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
            @RequestParam(required = false) MultipartFile file) throws IOException {
        byte[] media = file == null || file.isEmpty() ? null : file.getBytes();
        Lesson lesson = catalog.createLesson(
                RequestActor.from(authentication),
                moduleId,
                title,
                kind,
                markdown,
                media);
        return ResponseEntity.status(HttpStatus.CREATED).body(LessonBody.from(lesson));
    }

    @GetMapping("/lessons/{id}")
    LessonTextBody lesson(Authentication authentication, @PathVariable UUID id) {
        return LessonTextBody.from(catalog.lesson(RequestActor.from(authentication), id));
    }

    @GetMapping("/lessons/{id}/media")
    ResponseEntity<byte[]> media(Authentication authentication, @PathVariable UUID id) {
        LessonFile file = catalog.media(RequestActor.from(authentication), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType())).body(file.content());
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
