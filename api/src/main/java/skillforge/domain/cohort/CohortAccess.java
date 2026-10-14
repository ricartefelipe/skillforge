package skillforge.domain.cohort;

import skillforge.domain.identity.Role;

public final class CohortAccess {

    private CohortAccess() {
    }

    public static boolean canSee(Role role, String teacher, String actor, boolean enrolled) {
        return switch (role) {
            case ADMINISTRACAO -> true;
            case DOCENTE -> teacher.equals(actor);
            case ALUNO -> enrolled;
        };
    }

    public static boolean canLead(Role role, String teacher, String actor) {
        return switch (role) {
            case ADMINISTRACAO -> true;
            case DOCENTE -> teacher.equals(actor);
            case ALUNO -> false;
        };
    }

}
