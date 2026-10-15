package skillforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import skillforge.application.catalog.CatalogApplication;
import skillforge.application.catalog.ForumApplication;
import skillforge.application.cohort.ClassroomApplication;
import skillforge.application.cohort.CohortApplication;
import skillforge.application.cohort.ExamApplication;
import skillforge.application.cohort.PracticeApplication;
import skillforge.application.studio.StudioApplication;
import skillforge.domain.cohort.ClassroomRepository;
import skillforge.domain.cohort.ExamRepository;
import skillforge.domain.cohort.PracticeRepository;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.forum.ForumRepository;
import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.studio.StudioRepository;

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

    @Bean
    StudioApplication studioApplication(StudioRepository studio) {
        return new StudioApplication(studio);
    }

    @Bean
    PracticeApplication practiceApplication(PracticeRepository practices, StudioApplication studio) {
        return new PracticeApplication(practices, studio);
    }

    @Bean
    ExamApplication examApplication(ExamRepository exams, StudioApplication studio) {
        return new ExamApplication(exams, studio);
    }

    @Bean
    ClassroomApplication classroomApplication(
            CohortRepository cohorts,
            ClassroomRepository classroom,
            StudioApplication studio) {
        return new ClassroomApplication(cohorts, classroom, studio);
    }

}
