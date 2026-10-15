package skillforge.application.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.Lesson;
import skillforge.domain.catalog.LessonAsset;
import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.catalog.LessonKind;
import skillforge.domain.catalog.ModuleOrder;
import skillforge.domain.catalog.SignedLaunch;
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
            byte[] media,
            String launchUrl,
            String secret) {
        UUID courseId = courses.courseOfModule(moduleId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        requireWriter(actor, courseId);
        String title = Names.require(rawTitle, Failure.BLANK_TITLE);
        LessonContent.Decision decision = LessonContent.decide(kind, markdown, media, bounds.current());
        return switch (decision) {
            case LessonContent.Rejected rejected -> throw new Rejection(rejected.failure());
            case LessonContent.Accepted accepted when accepted.kind() == LessonKind.SCORM -> new Lesson(
                    courses.addScorm(moduleId, title, accepted.media()),
                    title,
                    LessonKind.SCORM);
            case LessonContent.Accepted accepted when accepted.kind() == LessonKind.LTI -> openTool(
                    moduleId, title, accepted, launchUrl, secret);
            case LessonContent.Accepted accepted -> new Lesson(
                    courses.addLesson(moduleId, title, accepted),
                    title,
                    accepted.kind());
        };
    }

    public String scormLaunch(Actor actor, UUID lessonId) {
        Objects.requireNonNull(actor, "actor");
        courses.lesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        return courses.scormLaunch(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public LessonFile scormFile(Actor actor, UUID lessonId, String path) {
        Objects.requireNonNull(actor, "actor");
        return courses.scormFile(lessonId, path).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public void trackScorm(Actor actor, UUID lessonId, String status, Integer score) {
        courses.lesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        if (status == null || !java.util.Set.of("completed", "passed", "failed", "incomplete").contains(status)) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        if (score != null && (score < 0 || score > 100)) {
            throw new Rejection(Failure.INVALID_GRADE);
        }
        courses.trackScorm(lessonId, actor.username(), status, score);
    }

    public SignedLaunch launch(Actor actor, UUID lessonId) {
        courses.lesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        String url = courses.toolUrl(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        String secret = courses.toolSecret(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        String role = switch (actor.role()) {
            case ALUNO -> "Learner";
            case DOCENTE -> "Instructor";
            case ADMINISTRACAO -> "Administrator";
        };
        return new SignedLaunch(url, skillforge.domain.catalog.LtiLaunch.sign(
                url,
                secret,
                actor.username(),
                role,
                lessonId.toString(),
                java.util.UUID.randomUUID().toString(),
                java.time.Instant.now().getEpochSecond()));
    }

    public void reorder(Actor actor, UUID courseId, java.util.List<ModuleOrder> modules) {
        requireWriter(actor, courseId);
        if (modules == null || modules.isEmpty()) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        courses.reorder(courseId, modules);
    }

    private Lesson openTool(
            UUID moduleId,
            String title,
            LessonContent.Accepted accepted,
            String launchUrl,
            String secret) {
        if (launchUrl == null
                || !(launchUrl.startsWith("http://") || launchUrl.startsWith("https://"))
                || secret == null
                || secret.length() < 8
                || secret.length() > Names.MAX) {
            throw new Rejection(Failure.LAUNCH);
        }
        UUID id = courses.addLesson(moduleId, title, accepted);
        courses.saveTool(id, launchUrl.trim(), secret);
        return new Lesson(id, title, LessonKind.LTI);
    }

    public void removeLesson(Actor actor, UUID lessonId) {
        UUID courseId = courses.courseOfLesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        requireWriter(actor, courseId);
        courses.removeLesson(lessonId);
    }

    public void reviseLesson(Actor actor, UUID lessonId, String rawTitle, String markdown) {
        UUID courseId = courses.courseOfLesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        requireWriter(actor, courseId);
        String title = Names.require(rawTitle, Failure.BLANK_TITLE);
        String text = null;
        if (markdown != null) {
            text = switch (LessonContent.decide("markdown", markdown, null, bounds.current())) {
                case LessonContent.Rejected rejected -> throw new Rejection(rejected.failure());
                case LessonContent.Accepted accepted -> accepted.markdown();
            };
        }
        courses.reviseLesson(lessonId, title, text);
    }

    public LessonAsset addAsset(Actor actor, UUID lessonId, String rawName, String kind, byte[] media) {
        UUID courseId = courses.courseOfLesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        requireWriter(actor, courseId);
        String name = Names.require(rawName, Failure.BLANK_TITLE);
        LessonContent.Accepted accepted = switch (LessonContent.decide(kind, null, media, bounds.current())) {
            case LessonContent.Rejected rejected -> throw new Rejection(rejected.failure());
            case LessonContent.Accepted file -> file;
        };
        if (accepted.kind() == LessonKind.MARKDOWN) {
            throw new Rejection(Failure.FILE_ONLY);
        }
        return new LessonAsset(
                courses.addAsset(lessonId, name, accepted.media(), accepted.mediaType()),
                name,
                accepted.mediaType());
    }

    public List<LessonAsset> assets(Actor actor, UUID lessonId) {
        Objects.requireNonNull(actor, "actor");
        courses.lesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        return courses.assets(lessonId);
    }

    public LessonFile asset(Actor actor, UUID lessonId, UUID assetId) {
        Objects.requireNonNull(actor, "actor");
        return courses.asset(lessonId, assetId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public void removeAsset(Actor actor, UUID lessonId, UUID assetId) {
        UUID courseId = courses.courseOfLesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        requireWriter(actor, courseId);
        courses.removeAsset(lessonId, assetId);
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
