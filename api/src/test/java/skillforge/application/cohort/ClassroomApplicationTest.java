package skillforge.application.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import skillforge.application.studio.RecordingStudio;
import skillforge.application.studio.StudioApplication;
import skillforge.domain.cohort.Certificate;
import skillforge.domain.cohort.ClassroomRepository;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.cohort.Presence;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Rejection;

class ClassroomApplicationTest {

    private final RecordingStudio store = new RecordingStudio();

    private final UUID cohort = UUID.randomUUID();

    private final ClassroomApplication classroom = new ClassroomApplication(
            new ClosedCohorts(),
            new MemoryClassroom(),
            new StudioApplication(store));

    @Test
    void anAnnouncementTellsTheCohortAndAStudentCannotPublishIt() {
        Actor teacher = new Actor(Role.DOCENTE, "docente");
        UUID id = classroom.announce(teacher, cohort, " Aviso ", " Tragam a peça. ");

        assertEquals(cohort, id);
        assertEquals("Novo aviso da turma.", store.notice);
        assertEquals(
                "forbidden",
                assertThrows(
                        Rejection.class,
                        () -> classroom.announce(new Actor(Role.ALUNO, "aluno"), cohort, "Aviso", "Texto"))
                        .failure()
                        .wire());
    }

    @Test
    void aCallNeedsADayAndANamedStudent() {
        assertEquals(
                "blank-student",
                assertThrows(Rejection.class, () -> classroom.call(
                        new Actor(Role.DOCENTE, "docente"),
                        cohort,
                        null,
                        List.of(new Presence("aluno", true))))
                        .failure()
                        .wire());
    }

    @Test
    void onlyAStudentReceivesACertificate() {
        assertEquals(
                "forbidden",
                assertThrows(Rejection.class, () -> classroom.certificate(new Actor(Role.DOCENTE, "docente"), cohort))
                        .failure()
                        .wire());
        assertEquals(
                "not-certified",
                assertThrows(Rejection.class, () -> classroom.certificate(new Actor(Role.ALUNO, "aluno"), cohort))
                        .failure()
                        .wire());
    }

    private final class ClosedCohorts implements CohortRepository {

        @Override
        public UUID open(UUID courseId, String name, String teacher) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enroll(UUID cohortId, String student) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<skillforge.domain.cohort.CohortListing> visible(Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<skillforge.domain.cohort.CohortDetail> page(UUID id, Actor actor) {
            return Optional.empty();
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
        public java.util.Set<UUID> courseIds(Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<skillforge.domain.cohort.Report> report(Actor actor) {
            throw new UnsupportedOperationException();
        }

    }

    private final class MemoryClassroom implements ClassroomRepository {

        @Override
        public UUID announce(UUID cohortId, Actor actor, String title, String body) {
            assertEquals("Aviso", title);
            return cohortId;
        }

        @Override
        public void withdraw(UUID cohortId, Actor actor, UUID announcementId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID call(UUID cohortId, Actor actor, LocalDate heldOn, java.util.List<Presence> marks) {
            return cohortId;
        }

        @Override
        public Optional<Certificate> certificate(UUID cohortId, Actor actor) {
            return Optional.empty();
        }

        @Override
        public java.util.List<skillforge.domain.cohort.Announcement> announcements(UUID cohortId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<skillforge.domain.cohort.Roll> rolls(UUID cohortId, Actor actor) {
            throw new UnsupportedOperationException();
        }

    }

}
