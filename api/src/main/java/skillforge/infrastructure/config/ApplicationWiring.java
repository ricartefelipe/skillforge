package skillforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import skillforge.application.bank.BankApplication;
import skillforge.application.catalog.CatalogApplication;
import skillforge.application.catalog.ForumApplication;
import skillforge.application.cohort.ClassroomApplication;
import skillforge.application.cohort.CohortApplication;
import skillforge.application.cohort.ExamApplication;
import skillforge.application.cohort.PracticeApplication;
import skillforge.application.group.GroupApplication;
import skillforge.application.mail.MailApplication;
import skillforge.application.notice.NoticeApplication;
import skillforge.application.resume.ResumeApplication;
import skillforge.application.search.SearchApplication;
import skillforge.domain.bank.BankRepository;
import skillforge.domain.catalog.CourseRepository;
import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.cohort.ClassroomRepository;
import skillforge.domain.cohort.CohortRepository;
import skillforge.domain.cohort.CourseAccess;
import skillforge.domain.cohort.ExamRepository;
import skillforge.domain.cohort.LessonProgress;
import skillforge.domain.cohort.PracticeRepository;
import skillforge.domain.forum.ForumRepository;
import skillforge.domain.group.GroupRepository;
import skillforge.domain.mail.MailRepository;
import skillforge.domain.notice.NoticeRepository;
import skillforge.domain.resume.ResumeRepository;
import skillforge.domain.search.SearchRepository;

@Configuration
class ApplicationWiring {

    @Bean
    CatalogApplication catalogApplication(CourseRepository courses, CourseAccess access, LessonBounds bounds) {
        return new CatalogApplication(courses, access, bounds);
    }

    @Bean
    ForumApplication forumApplication(ForumRepository forum, CourseAccess access) {
        return new ForumApplication(forum, access);
    }

    @Bean
    CohortApplication cohortApplication(CohortRepository cohorts) {
        return new CohortApplication(cohorts);
    }

    @Bean
    PracticeApplication practiceApplication(PracticeRepository practices, NoticeRepository notices) {
        return new PracticeApplication(practices, notices);
    }

    @Bean
    ExamApplication examApplication(
            ExamRepository exams,
            BankRepository bank,
            NoticeRepository notices,
            LessonProgress lessons) {
        return new ExamApplication(exams, bank, notices, lessons);
    }

    @Bean
    ClassroomApplication classroomApplication(
            CohortRepository cohorts,
            ClassroomRepository classroom,
            NoticeRepository notices) {
        return new ClassroomApplication(cohorts, classroom, notices);
    }

    @Bean
    ResumeApplication resumeApplication(ResumeRepository resumes) {
        return new ResumeApplication(resumes);
    }

    @Bean
    NoticeApplication noticeApplication(NoticeRepository notices) {
        return new NoticeApplication(notices);
    }

    @Bean
    MailApplication mailApplication(MailRepository mail) {
        return new MailApplication(mail);
    }

    @Bean
    SearchApplication searchApplication(SearchRepository search) {
        return new SearchApplication(search);
    }

    @Bean
    BankApplication bankApplication(BankRepository bank) {
        return new BankApplication(bank);
    }

    @Bean
    GroupApplication groupApplication(GroupRepository groups) {
        return new GroupApplication(groups);
    }

}
