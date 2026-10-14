package skillforge.application.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.Lesson;
import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.catalog.LessonContent;
import skillforge.domain.catalog.LessonFile;
import skillforge.domain.catalog.LessonText;
import skillforge.domain.identity.Actor;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;

public class CatalogApplication {

    private final CourseRepository courses;

    private final LessonBounds bounds;

    public CatalogApplication(CourseRepository courses, LessonBounds bounds) {
        this.courses = courses;
        this.bounds = bounds;
    }

    public List<Course> list(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        return courses.list();
    }

    public Created createCourse(Actor actor, String rawName) {
        actor.requireAdministration();
        String name = Names.require(rawName, Failure.BLANK_COURSE);
        return new Created(courses.addCourse(name), name);
    }

    public Created createModule(Actor actor, UUID courseId, String rawName) {
        actor.requireAdministration();
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
        actor.requireAdministration();
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

    public LessonText lesson(Actor actor, UUID id) {
        Objects.requireNonNull(actor, "actor");
        return courses.lesson(id).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public LessonFile media(Actor actor, UUID id) {
        Objects.requireNonNull(actor, "actor");
        return courses.media(id).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public record Created(UUID id, String name) {
    }

}
