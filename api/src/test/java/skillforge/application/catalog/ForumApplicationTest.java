package skillforge.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.forum.ForumRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Rejection;

class ForumApplicationTest {

    private final UUID lesson = UUID.randomUUID();

    private final UUID course = UUID.randomUUID();

    private final UUID doubt = UUID.randomUUID();

    private final ForumApplication forum = new ForumApplication(new MemoryForum(), new MemoryCohorts());

    @Test
    void anOutsiderCannotOpenADoubt() {
        Rejection rejection = assertThrows(
                Rejection.class,
                () -> forum.open(new Actor(Role.ALUNO, "fora"), lesson, "Dúvida", "Texto"));

        assertEquals("not-found", rejection.failure().wire());
    }

    @Test
    void aMemberOpensADoubtAndCannotReplyToAnotherLesson() {
        UUID opened = forum.open(new Actor(Role.ALUNO, "aluno"), lesson, " Dúvida ", " Texto ");

        assertEquals(doubt, opened);
        assertEquals(
                "not-found",
                assertThrows(
                        Rejection.class,
                        () -> forum.reply(new Actor(Role.ALUNO, "aluno"), lesson, UUID.randomUUID(), "Resposta"))
                        .failure()
                        .wire());
    }

    private final class MemoryForum implements ForumRepository {

        @Override
        public List<skillforge.domain.forum.Doubt> list(UUID lessonId) {
            return List.of();
        }

        @Override
        public UUID open(UUID lessonId, String author, String title, String body) {
            assertEquals("Dúvida", title);
            assertEquals("Texto", body);
            return doubt;
        }

        @Override
        public UUID reply(UUID doubtId, String author, String body) {
            return UUID.randomUUID();
        }

        @Override
        public Optional<UUID> courseOfLesson(UUID lessonId) {
            return lesson.equals(lessonId) ? Optional.of(course) : Optional.empty();
        }

        @Override
        public boolean doubtOnLesson(UUID lessonId, UUID doubtId) {
            return doubt.equals(doubtId);
        }

    }

    private final class MemoryCohorts implements CohortRepository {

        @Override
        public UUID open(UUID courseId, String name, String teacher) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enroll(UUID cohortId, String student) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<skillforge.domain.cohort.CohortListing> visible(Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<skillforge.domain.cohort.CohortDetail> page(UUID id, Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(UUID cohortId, UUID lessonId, String student) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean teaches(String teacher, UUID courseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> courseIds(Actor actor) {
            return "aluno".equals(actor.username()) ? Set.of(course) : Set.of();
        }

        @Override
        public List<skillforge.domain.cohort.Report> report(Actor actor) {
            throw new UnsupportedOperationException();
        }

    }

}
