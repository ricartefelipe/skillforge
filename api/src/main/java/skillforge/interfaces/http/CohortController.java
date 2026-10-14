package skillforge.interfaces.http;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import skillforge.application.cohort.CohortApplication;
import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.Module;
import skillforge.domain.cohort.CohortDetail;
import skillforge.domain.cohort.CohortListing;
import skillforge.domain.cohort.CohortSummary;
import skillforge.domain.cohort.Progress;
import skillforge.domain.identity.Actor;

@RestController
class CohortController {

    private final CohortApplication cohorts;

    CohortController(CohortApplication cohorts) {
        this.cohorts = cohorts;
    }

    @GetMapping("/cohorts")
    List<CohortBody> cohorts(Authentication authentication) {
        Actor actor = RequestActor.from(authentication);
        return cohorts.list(actor).stream().map(CohortController::listing).toList();
    }

    @GetMapping("/cohorts/{id}")
    CohortBody cohort(Authentication authentication, @PathVariable UUID id) {
        return detail(cohorts.page(RequestActor.from(authentication), id));
    }

    @PostMapping("/cohorts/{id}/lessons/{lessonId}/completion")
    ResponseEntity<Void> complete(Authentication authentication, @PathVariable UUID id, @PathVariable UUID lessonId) {
        cohorts.complete(RequestActor.from(authentication), id, lessonId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cohorts")
    ResponseEntity<IdName> open(Authentication authentication, @RequestBody OpenBody body) {
        OpenBody request = body == null ? new OpenBody(null, null, null) : body;
        CohortApplication.Opened opened = cohorts.open(
                RequestActor.from(authentication),
                request.courseId(),
                request.name(),
                request.teacher());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(opened.id(), opened.name()));
    }

    @PostMapping("/cohorts/{id}/enrollments")
    ResponseEntity<Void> enroll(Authentication authentication, @PathVariable UUID id, @RequestBody EnrollBody body) {
        cohorts.enroll(RequestActor.from(authentication), id, body == null ? null : body.student());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static CohortBody listing(CohortListing listing) {
        return switch (listing) {
            case CohortListing.Student student -> body(
                    student.summary(), null, student.completed(), student.total(), null);
            case CohortListing.Staff staff -> body(staff.summary(), staff.students(), null, null, null);
        };
    }

    private static CohortBody detail(CohortDetail page) {
        return switch (page) {
            case CohortDetail.Student student -> body(
                    student.summary(),
                    null,
                    student.completed(),
                    student.total(),
                    course(student.course(), student.finished()));
            case CohortDetail.Staff staff -> body(
                    staff.summary(), staff.students(), null, null, course(staff.course(), null));
        };
    }

    private static CohortBody body(
            CohortSummary summary,
            List<Progress> students,
            Integer completed,
            Integer total,
            CourseBody course) {
        List<ProgressBody> roster = students == null
                ? null
                : students.stream().map(ProgressBody::from).toList();
        return new CohortBody(
                summary.id(),
                summary.name(),
                summary.courseId(),
                summary.courseName(),
                summary.teacher(),
                roster,
                completed,
                total,
                course);
    }

    private static CourseBody course(Course course, Set<UUID> finished) {
        return new CourseBody(
                course.id(),
                course.name(),
                course.modules().stream().map(module -> module(module, finished)).toList());
    }

    private static ModuleBody module(Module module, Set<UUID> finished) {
        return new ModuleBody(
                module.id(),
                module.name(),
                module.lessons().stream().map(lesson -> new LessonBody(
                        lesson.id(),
                        lesson.title(),
                        lesson.kind().wire(),
                        finished == null ? null : finished.contains(lesson.id()))).toList());
    }

    record OpenBody(String name, UUID courseId, String teacher) {
    }

    record IdName(UUID id, String name) {
    }

    record EnrollBody(String student) {
    }

    record ProgressBody(String student, int completed, int total) {

        static ProgressBody from(Progress progress) {
            return new ProgressBody(progress.student(), progress.completed(), progress.total());
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LessonBody(UUID id, String title, String kind, Boolean completed) {
    }

    record ModuleBody(UUID id, String name, List<LessonBody> lessons) {
    }

    record CourseBody(UUID id, String name, List<ModuleBody> modules) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CohortBody(
            UUID id,
            String name,
            UUID courseId,
            String courseName,
            String teacher,
            List<ProgressBody> students,
            Integer completed,
            Integer total,
            CourseBody course) {
    }

}
