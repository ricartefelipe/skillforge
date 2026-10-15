package skillforge.domain.cohort;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Rejection;

class CohortAccessRulesTest {

    @Test
    void administrationSeesAndLeadsEveryCohort() {
        assertTrue(CohortAccess.canSee(Role.ADMINISTRACAO, "docente", "administracao", false));
        assertTrue(CohortAccess.canLead(Role.ADMINISTRACAO, "docente", "administracao"));
    }

    @Test
    void aTeacherSeesAndLeadsOnlyTheCohortTheyTeach() {
        assertTrue(CohortAccess.canSee(Role.DOCENTE, "docente", "docente", false));
        assertTrue(CohortAccess.canLead(Role.DOCENTE, "docente", "docente"));
        assertFalse(CohortAccess.canSee(Role.DOCENTE, "docente", "outro", false));
        assertFalse(CohortAccess.canLead(Role.DOCENTE, "docente", "outro"));
    }

    @Test
    void aStudentSeesOnlyAnEnrollmentAndNeverLeads() {
        assertTrue(CohortAccess.canSee(Role.ALUNO, "docente", "aluno", true));
        assertFalse(CohortAccess.canSee(Role.ALUNO, "docente", "aluno", false));
        assertFalse(CohortAccess.canLead(Role.ALUNO, "docente", "aluno"));
    }

    @Test
    void onlyAdministrationOpensACourseAndOnlyAStudentSitsAnExam() {
        assertThrows(Rejection.class, () -> new Actor(Role.DOCENTE, "docente").requireAdministration());
        new Actor(Role.ADMINISTRACAO, "administracao").requireAdministration();
        assertThrows(Rejection.class, () -> new Actor(Role.DOCENTE, "docente").requireStudent());
        new Actor(Role.ALUNO, "aluno").requireStudent();
    }

}
