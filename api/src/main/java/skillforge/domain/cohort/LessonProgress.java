package skillforge.domain.cohort;

import java.util.UUID;

public interface LessonProgress {

    boolean finished(UUID cohortId, String student);

}
