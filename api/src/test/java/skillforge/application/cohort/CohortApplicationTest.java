package skillforge.application.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Rejection;

class CohortApplicationTest {

    private final UUID opened = UUID.randomUUID();

    private final CohortApplication cohorts = new CohortApplication(new MemoryCohorts());

    @Test
    void onlyAdministrationOpensAndEnrolls() {
        Actor admin = new Actor(Role.ADMINISTRACAO, "administracao");
        Opened created = cohorts.open(admin, UUID.randomUUID(), " Manhã ", " docente ");

        assertEquals(opened, created.id());
        assertEquals("Manhã", created.name());
        cohorts.enroll(admin, opened, " aluno ");
        Actor teacher = new Actor(Role.DOCENTE, "docente");
        assertEquals(
                "forbidden",
                assertThrows(Rejection.class, () -> cohorts.open(teacher, opened, "Manhã", "docente"))
                        .failure()
                        .wire());
        assertEquals(
                "blank-cohort",
                assertThrows(Rejection.class, () -> cohorts.open(admin, UUID.randomUUID(), " ", "docente"))
                        .failure()
                        .wire());
        assertEquals(
                "not-found",
                assertThrows(Rejection.class, () -> cohorts.page(admin, opened)).failure().wire());
    }

    private final class MemoryCohorts implements CohortRepository {

        @Override
        public UUID open(UUID courseId, String name, String teacher) {
            assertEquals("Manhã", name);
            assertEquals("docente", teacher);
            return opened;
        }

        @Override
        public void enroll(UUID cohortId, String student) {
            assertEquals("aluno", student);
        }

        @Override
        public java.util.List<skillforge.domain.cohort.CohortListing> visible(Actor actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<skillforge.domain.cohort.CohortDetail> page(UUID id, Actor actor) {
            return java.util.Optional.empty();
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

}
