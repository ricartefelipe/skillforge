package skillforge.application.catalog;

import java.util.List;
import java.util.UUID;

import skillforge.domain.forum.Doubt;
import skillforge.domain.forum.ForumRepository;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;

public class ForumApplication {

    private final ForumRepository forum;

    private final CohortRepository cohorts;

    public ForumApplication(ForumRepository forum, CohortRepository cohorts) {
        this.forum = forum;
        this.cohorts = cohorts;
    }

    public List<Doubt> list(Actor actor, UUID lessonId) {
        requireMember(actor, lessonId);
        return forum.list(lessonId);
    }

    public UUID open(Actor actor, UUID lessonId, String rawTitle, String rawBody) {
        requireMember(actor, lessonId);
        return forum.open(
                lessonId,
                actor.username(),
                Names.require(rawTitle, Failure.BLANK_TITLE),
                Texts.require(rawBody, Failure.BLANK_PROMPT));
    }

    public UUID reply(Actor actor, UUID lessonId, UUID doubtId, String rawBody) {
        requireMember(actor, lessonId);
        if (!forum.doubtOnLesson(lessonId, doubtId)) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return forum.reply(doubtId, actor.username(), Texts.require(rawBody, Failure.BLANK_PROMPT));
    }

    private void requireMember(Actor actor, UUID lessonId) {
        UUID courseId = forum.courseOfLesson(lessonId).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        if (actor.role() == Role.ADMINISTRACAO || cohorts.courseIds(actor).contains(courseId)) {
            return;
        }
        throw new Rejection(Failure.NOT_FOUND);
    }

}
