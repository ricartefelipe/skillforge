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
import skillforge.domain.catalog.ScormOutline;
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
            String secret,
            String authLogin) {
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
                    moduleId, title, accepted, launchUrl, secret, authLogin);
            case LessonContent.Accepted accepted -> new Lesson(
                    courses.addLesson(moduleId, title, accepted),
                    title,
                    accepted.kind());
        };
    }

    public ScormOutline scormLaunch(Actor actor, UUID lessonId) {
        Objects.requireNonNull(actor, "actor");
        courses.lesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        ScormOutline outline = courses.scormOutline(lessonId, actor.username());
        if (outline.path().isBlank()) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return outline;
    }

    public LessonFile scormFile(Actor actor, UUID lessonId, String path) {
        Objects.requireNonNull(actor, "actor");
        return courses.scormFile(lessonId, path).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
    }

    public void trackScorm(Actor actor, UUID lessonId, String status, Integer score, int position) {
        courses.lesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        if (status == null || !java.util.Set.of("completed", "passed", "failed", "incomplete").contains(status)) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        if (score != null && (score < 0 || score > 100)) {
            throw new Rejection(Failure.INVALID_GRADE);
        }
        courses.trackScorm(lessonId, actor.username(), status, score, Math.max(position, 1));
    }

    public SignedLaunch launch(Actor actor, UUID lessonId, String iss) {
        courses.lesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        String url = courses.toolUrl(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        String secret = courses.toolSecret(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        java.util.Optional<String> auth = courses.toolAuth(lessonId);
        if (auth.isPresent()) {
            java.util.UUID hint = java.util.UUID.randomUUID();
            String nonce = java.util.UUID.randomUUID().toString();
            courses.rememberHint(hint, actor.username(), lessonId, nonce);
            java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("iss", iss);
            fields.put("login_hint", actor.username());
            fields.put("target_link_uri", url);
            fields.put("client_id", "skillforge");
            fields.put("lti_message_hint", hint.toString());
            return new SignedLaunch(auth.get(), java.util.Map.copyOf(fields));
        }
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

    public String jwks() {
        return skillforge.domain.catalog.LtiToken.jwk(courses.platformKid(), courses.platformPublic());
    }

    public String authorize(java.util.UUID hint, String target, String iss) {
        String raw = courses.takeHint(hint).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        String[] parts = raw.split("\\|", 3);
        long now = java.time.Instant.now().getEpochSecond();
        String payload = "{\"iss\":\"" + iss + "\",\"aud\":\"skillforge\",\"sub\":\"" + parts[0]
                + "\",\"nonce\":\"" + parts[2] + "\",\"iat\":" + now + ",\"exp\":" + (now + 600)
                + ",\"https://purl.imsglobal.org/spec/lti/claim/message_type\":\"LtiResourceLinkRequest\""
                + ",\"https://purl.imsglobal.org/spec/lti/claim/version\":\"1.3.0\""
                + ",\"https://purl.imsglobal.org/spec/lti/claim/deployment_id\":\"skillforge\""
                + ",\"https://purl.imsglobal.org/spec/lti/claim/target_link_uri\":\"" + target + "\"}";
        String token = skillforge.domain.catalog.LtiToken.sign(
                courses.platformKid(),
                courses.platformPrivate(),
                payload);
        return """
                <!doctype html><html><body>
                <form id="go" method="post" action="%s">
                <input type="hidden" name="id_token" value="%s">
                </form><script>document.getElementById('go').submit()</script>
                </body></html>
                """.formatted(target, token);
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
            String secret,
            String authLogin) {
        if (launchUrl == null
                || !(launchUrl.startsWith("http://") || launchUrl.startsWith("https://"))
                || secret == null
                || secret.length() < 8
                || secret.length() > Names.MAX) {
            throw new Rejection(Failure.LAUNCH);
        }
        UUID id = courses.addLesson(moduleId, title, accepted);
        courses.saveTool(id, launchUrl.trim(), secret);
        if (authLogin != null && (authLogin.startsWith("http://") || authLogin.startsWith("https://"))) {
            courses.modernTool(id, authLogin.trim());
        }
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
