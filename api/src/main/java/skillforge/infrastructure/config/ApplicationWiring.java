package skillforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import skillforge.application.catalog.CatalogApplication;
import skillforge.application.catalog.ForumApplication;
import skillforge.application.cohort.CohortApplication;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.ForumRepository;
import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.cohort.CohortRepository;

@Configuration
class ApplicationWiring {

    @Bean
    CatalogApplication catalogApplication(CourseRepository courses, CohortRepository cohorts, LessonBounds bounds) {
        return new CatalogApplication(courses, cohorts, bounds);
    }

    @Bean
    ForumApplication forumApplication(ForumRepository forum, CohortRepository cohorts) {
        return new ForumApplication(forum, cohorts);
    }

    @Bean
    CohortApplication cohortApplication(CohortRepository cohorts) {
        return new CohortApplication(cohorts);
    }

}
