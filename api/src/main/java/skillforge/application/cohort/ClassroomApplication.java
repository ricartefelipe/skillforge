package skillforge.application.cohort;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import skillforge.application.studio.StudioApplication;
import skillforge.domain.cohort.Announcement;
import skillforge.domain.cohort.Certificate;
import skillforge.domain.cohort.ClassroomRepository;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.cohort.Presence;
import skillforge.domain.cohort.Roll;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Names;
import skillforge.domain.shared.Rejection;
import skillforge.domain.shared.Texts;

public class ClassroomApplication {

    private final CohortRepository cohorts;

    private final ClassroomRepository classroom;

    private final StudioApplication studio;

    public ClassroomApplication(CohortRepository cohorts, ClassroomRepository classroom, StudioApplication studio) {
        this.cohorts = cohorts;
        this.classroom = classroom;
        this.studio = studio;
    }

    public void withdraw(Actor actor, UUID cohortId, UUID announcementId) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        classroom.withdraw(cohortId, actor, announcementId);
    }

    public UUID announce(Actor actor, UUID cohortId, String rawTitle, String rawBody) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        UUID id = classroom.announce(
                cohortId,
                actor,
                Names.require(rawTitle, Failure.BLANK_TITLE),
                Texts.require(rawBody, Failure.BLANK_PROMPT));
        studio.tellCohort(cohortId, "Novo aviso da turma.");
        return id;
    }

    public UUID call(Actor actor, UUID cohortId, LocalDate heldOn, List<Presence> marks) {
        if (actor.role() == Role.ALUNO) {
            throw new Rejection(Failure.FORBIDDEN);
        }
        if (heldOn == null || marks == null || marks.isEmpty()) {
            throw new Rejection(Failure.BLANK_STUDENT);
        }
        List<Presence> clean = new ArrayList<>();
        for (Presence mark : marks) {
            clean.add(new Presence(Names.require(mark.student(), Failure.BLANK_STUDENT), mark.present()));
        }
        return classroom.call(cohortId, actor, heldOn, clean);
    }

    public List<Announcement> announcements(Actor actor, UUID cohortId) {
        cohorts.page(cohortId, actor).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        return classroom.announcements(cohortId);
    }

    public List<Roll> rolls(Actor actor, UUID cohortId) {
        cohorts.page(cohortId, actor).orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
        return classroom.rolls(cohortId, actor);
    }

    public Certificate certificate(Actor actor, UUID cohortId) {
        actor.requireStudent();
        return classroom.certificate(cohortId, actor).orElseThrow(() -> new Rejection(Failure.NOT_CERTIFIED));
    }

}
