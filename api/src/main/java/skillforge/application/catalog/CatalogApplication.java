package skillforge.application.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.Lesson;
import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.catalog.LessonContent;
import skillforge.domain.catalog.LessonFile;
import skillforge.domain.catalog.LessonText;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;

public class CatalogApplication {

    private final CourseRepository courses;

    private final CohortRepository cohorts;

    private final LessonBounds bounds;

    public CatalogApplication(CourseRepository courses, CohortRepository cohorts, LessonBounds bounds) {
        this.courses = courses;
        this.cohorts = cohorts;
        this.bounds = bounds;
    }

    public List<Course> list(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        return courses.list();
    }

    public List<Course> curriculum(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() == Role.ADMINISTRACAO) {
            return courses.list();
        }
        Set<UUID> ids = cohorts.courseIds(actor);
        return courses.list().stream().filter(course -> ids.contains(course.id())).toList();
    }

    public Set<UUID> teaching(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() == Role.ADMINISTRACAO) {
            return courses.list().stream().map(Course::id).collect(Collectors.toSet());
        }
        if (actor.role() != Role.DOCENTE) {
            return Set.of();
        }
        return cohorts.courseIds(actor);
    }

    public Created createCourse(Actor actor, String rawName) {
        actor.requireAdministration();
        String name = Names.require(rawName, Failure.BLANK_COURSE);
        return new Created(courses.addCourse(name), name);
    }

    public Created createModule(Actor actor, UUID courseId, String rawName) {
        requireWriter(actor, courseId);
        String name = Names.require(rawName, Failure.BLANK_MODULE);
        return new Created(courses.addModule(courseId, name), name);
    }

    public Lesson createLesson(
            Actor actor,
            UUID moduleId,
            String rawTitle,
            String kind,
            String markdown,
            byte[] media) {
        UUID courseId = courses.courseOfModule(moduleId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        requireWriter(actor, courseId);
        String title = Names.require(rawTitle, Failure.BLANK_TITLE);
        LessonContent.Decision decision = LessonContent.decide(kind, markdown, media, bounds.current());
        return switch (decision) {
            case LessonContent.Rejected rejected -> throw new Rejection(rejected.failure());
            case LessonContent.Accepted accepted -> new Lesson(
                    courses.addLesson(moduleId, title, accepted),
                    title,
                    accepted.kind());
        };
    }

    public void removeLesson(Actor actor, UUID lessonId) {
        UUID courseId = courses.courseOfLesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        requireWriter(actor, courseId);
        courses.removeLesson(lessonId);
    }

    public LessonText lesson(Actor actor, UUID id) {
        Objects.requireNonNull(actor, "actor");
        return courses.lesson(id).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public LessonFile media(Actor actor, UUID id) {
        Objects.requireNonNull(actor, "actor");
        return courses.media(id).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    private void requireWriter(Actor actor, UUID courseId) {
        if (actor.role() == Role.ADMINISTRACAO) {
            return;
        }
        if (actor.role() == Role.DOCENTE && cohorts.teaches(actor.username(), courseId)) {
            return;
        }
        throw new Rejection(Failure.FORBIDDEN);
    }

    public record Created(UUID id, String name) {
    }

}
