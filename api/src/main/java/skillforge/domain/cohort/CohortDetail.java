package skillforge.domain.cohort;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import skillforge.domain.catalog.Course;

public sealed interface CohortDetail {

    CohortSummary summary();

    Course course();

    record Student(
            CohortSummary summary,
            int completed,
            int total,
            Course course,
            Set<UUID> finished,
            List<Practice> practices) implements CohortDetail {

        public Student {
            finished = Set.copyOf(finished);
            practices = List.copyOf(practices);
        }

    }

    record Staff(CohortSummary summary, List<Progress> students, Course course, List<Practice> practices)
            implements CohortDetail {

        public Staff {
            students = List.copyOf(students);
            practices = List.copyOf(practices);
        }

    }

}
