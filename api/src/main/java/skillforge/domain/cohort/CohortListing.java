package skillforge.domain.cohort;

import java.util.List;

public sealed interface CohortListing {

    CohortSummary summary();

    record Student(CohortSummary summary, int completed, int total) implements CohortListing {
    }

    record Staff(CohortSummary summary, List<Progress> students) implements CohortListing {

        public Staff {
            students = List.copyOf(students);
        }

    }

}
