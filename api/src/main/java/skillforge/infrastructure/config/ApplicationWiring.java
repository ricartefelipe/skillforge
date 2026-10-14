package skillforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import skillforge.application.catalog.CatalogApplication;
import skillforge.application.cohort.CohortApplication;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.cohort.CohortRepository;

@Configuration
class ApplicationWiring {

    @Bean
    CatalogApplication catalogApplication(CourseRepository courses, LessonBounds bounds) {
        return new CatalogApplication(courses, bounds);
    }

    @Bean
    CohortApplication cohortApplication(CohortRepository cohorts) {
        return new CohortApplication(cohorts);
    }

}
