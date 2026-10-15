package skillforge.interfaces.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import skillforge.application.cohort.ClassroomApplication;
import skillforge.application.cohort.CohortApplication;
import skillforge.application.cohort.ExamApplication;
import skillforge.application.cohort.Opened;
import skillforge.application.cohort.PracticeApplication;
import skillforge.domain.catalog.Course;
import skillforge.domain.catalog.Module;
import skillforge.domain.cohort.Announcement;
import skillforge.domain.cohort.Certificate;
import skillforge.domain.cohort.Choice;
import skillforge.domain.cohort.CohortDetail;
import skillforge.domain.cohort.CriterionMark;
import skillforge.domain.cohort.CohortListing;
import skillforge.domain.cohort.CohortSummary;
import skillforge.domain.cohort.Delivery;
import skillforge.domain.cohort.Exam;
import skillforge.domain.cohort.ExamScore;
import skillforge.domain.cohort.Presence;
import skillforge.domain.cohort.Roll;
import skillforge.domain.cohort.Practice;
import skillforge.domain.cohort.Progress;
import skillforge.domain.cohort.Report;
import skillforge.domain.cohort.Question;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;

@RestController
class CohortController {

    private final CohortApplication cohorts;

    private final PracticeApplication practices;

    private final ExamApplication exams;

    private final ClassroomApplication classroom;

    CohortController(
            CohortApplication cohorts,
            PracticeApplication practices,
            ExamApplication exams,
            ClassroomApplication classroom) {
        this.cohorts = cohorts;
        this.practices = practices;
        this.exams = exams;
        this.classroom = classroom;
    }

    @GetMapping("/grades")
    List<ReportBody> grades(Authentication authentication) {
        return cohorts.report(RequestActor.from(authentication)).stream().map(ReportBody::from).toList();
    }

    @GetMapping("/cohorts")
    List<CohortBody> cohorts(Authentication authentication) {
        Actor actor = RequestActor.from(authentication);
        return cohorts.list(actor).stream().map(CohortController::listing).toList();
    }

    @GetMapping("/cohorts/{id}")
    CohortBody cohort(Authentication authentication, @PathVariable UUID id) {
        Actor actor = RequestActor.from(authentication);
        return detail(cohorts.page(actor, id), actor.role() != Role.ALUNO);
    }

    @PostMapping("/cohorts/{id}/lessons/{lessonId}/completion")
    ResponseEntity<Void> complete(Authentication authentication, @PathVariable UUID id, @PathVariable UUID lessonId) {
        cohorts.complete(RequestActor.from(authentication), id, lessonId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cohorts")
    ResponseEntity<IdName> open(Authentication authentication, @RequestBody OpenBody body) {
        OpenBody request = body == null ? new OpenBody(null, null, null) : body;
        Opened opened = cohorts.open(
                RequestActor.from(authentication),
                request.courseId(),
                request.name(),
                request.teacher());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(opened.id(), opened.name()));
    }

    @PostMapping("/cohorts/{id}/enrollments")
    ResponseEntity<Void> enroll(Authentication authentication, @PathVariable UUID id, @RequestBody EnrollBody body) {
        cohorts.enroll(RequestActor.from(authentication), id, body == null ? null : body.student());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static CohortBody listing(CohortListing listing) {
        return switch (listing) {
            case CohortListing.Student student -> body(
                    student.summary(), null, student.completed(), student.total(), null, null, null);
            case CohortListing.Staff staff -> body(staff.summary(), staff.students(), null, null, null, null, null);
        };
    }

    private static CohortBody detail(CohortDetail page, boolean revealKey) {
        return switch (page) {
            case CohortDetail.Student student -> body(
                    student.summary(),
                    null,
                    student.completed(),
                    student.total(),
                    course(student.course(), student.finished()),
                    practices(student.practices()),
                    exams(student.exams(), revealKey));
            case CohortDetail.Staff staff -> body(
                    staff.summary(),
                    staff.students(),
                    null,
                    null,
                    course(staff.course(), null),
                    practices(staff.practices()),
                    exams(staff.exams(), revealKey));
        };
    }

    private static CohortBody body(
            CohortSummary summary,
            List<Progress> students,
            Integer completed,
            Integer total,
            CourseBody course,
            List<PracticeBody> practices,
            List<ExamBody> exams) {
        List<ProgressBody> roster = students == null
                ? null
                : students.stream().map(ProgressBody::from).toList();
        return new CohortBody(
                summary.id(),
                summary.name(),
                summary.courseId(),
                summary.courseName(),
                summary.teacher(),
                roster,
                completed,
                total,
                course,
                practices,
                exams);
    }

    private static CourseBody course(Course course, Set<UUID> finished) {
        return new CourseBody(
                course.id(),
                course.name(),
                course.modules().stream().map(module -> module(module, finished)).toList());
    }

    private static ModuleBody module(Module module, Set<UUID> finished) {
        return new ModuleBody(
                module.id(),
                module.name(),
                module.lessons().stream().map(lesson -> new LessonBody(
                        lesson.id(),
                        lesson.title(),
                        lesson.kind().wire(),
                        finished == null ? null : finished.contains(lesson.id()))).toList());
    }

    @PostMapping("/cohorts/{id}/practices")
    ResponseEntity<IdName> openPractice(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody PracticeRequest body) {
        PracticeRequest request = body == null ? new PracticeRequest(null, null, null, null) : body;
        java.time.Instant dueAt = request.dueAt() == null || request.dueAt().isBlank()
                ? null
                : java.time.Instant.parse(request.dueAt());
        Opened opened = practices.open(
                RequestActor.from(authentication),
                id,
                request.title(),
                request.prompt(),
                dueAt,
                request.criteria());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(opened.id(), opened.name()));
    }

    @DeleteMapping("/cohorts/{id}/practices/{practiceId}")
    ResponseEntity<Void> removePractice(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID practiceId) {
        practices.remove(RequestActor.from(authentication), id, practiceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cohorts/{id}/practices/{practiceId}/submission")
    ResponseEntity<Void> submit(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID practiceId,
            @RequestBody TextRequest body) {
        practices.submit(RequestActor.from(authentication), id, practiceId, body == null ? null : body.text());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/cohorts/{id}/practices/{practiceId}/feedback")
    ResponseEntity<Void> feedback(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID practiceId,
            @RequestBody FeedbackRequest body) {
        FeedbackRequest request = body == null ? new FeedbackRequest(null, null, null, null) : body;
        java.util.List<CriterionMark> marks = new java.util.ArrayList<>();
        if (request.marks() != null) {
            for (MarkRequest mark : request.marks()) {
                if (mark != null) {
                    marks.add(new CriterionMark(mark.criterionId(), mark.points()));
                }
            }
        }
        practices.feedback(
                RequestActor.from(authentication),
                id,
                practiceId,
                request.student(),
                request.text(),
                request.grade(),
                marks);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cohorts/{id}/exams")
    ResponseEntity<IdName> openExam(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody ExamRequest body) {
        ExamRequest request = body == null ? new ExamRequest(null, null, null) : body;
        java.time.Instant dueAt = request.dueAt() == null || request.dueAt().isBlank()
                ? null
                : java.time.Instant.parse(request.dueAt());
        Opened opened = exams.open(
                RequestActor.from(authentication),
                id,
                request.title(),
                dueAt,
                request.questions());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(opened.id(), opened.name()));
    }

    @DeleteMapping("/cohorts/{id}/exams/{examId}")
    ResponseEntity<Void> removeExam(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID examId) {
        exams.remove(RequestActor.from(authentication), id, examId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cohorts/{id}/exams/{examId}/attempt")
    ResponseEntity<Void> takeExam(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID examId,
            @RequestBody AttemptRequest body) {
        Map<UUID, UUID> answers = new HashMap<>();
        if (body != null && body.answers() != null) {
            for (AnswerRequest answer : body.answers()) {
                if (answer != null && answer.questionId() != null && answer.choiceId() != null) {
                    answers.put(answer.questionId(), answer.choiceId());
                }
            }
        }
        exams.take(RequestActor.from(authentication), id, examId, answers);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/cohorts/{id}/announcements")
    List<Announcement> announcements(Authentication authentication, @PathVariable UUID id) {
        return classroom.announcements(RequestActor.from(authentication), id);
    }

    @PostMapping("/cohorts/{id}/announcements")
    ResponseEntity<IdName> announce(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody NoticeRequest body) {
        NoticeRequest request = body == null ? new NoticeRequest(null, null) : body;
        UUID created = classroom.announce(RequestActor.from(authentication), id, request.title(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdName(created, request.title()));
    }

    @DeleteMapping("/cohorts/{id}/announcements/{announcementId}")
    ResponseEntity<Void> withdraw(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID announcementId) {
        classroom.withdraw(RequestActor.from(authentication), id, announcementId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cohorts/{id}/attendance")
    ResponseEntity<Void> call(Authentication authentication, @PathVariable UUID id, @RequestBody CallRequest body) {
        CallRequest request = body == null ? new CallRequest(null, null) : body;
        classroom.call(RequestActor.from(authentication), id, request.heldOn(), request.marks());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/cohorts/{id}/attendance")
    List<Roll> attendance(Authentication authentication, @PathVariable UUID id) {
        return classroom.rolls(RequestActor.from(authentication), id);
    }

    @GetMapping("/cohorts/{id}/certificate")
    Certificate certificate(Authentication authentication, @PathVariable UUID id) {
        return classroom.certificate(RequestActor.from(authentication), id);
    }

    private static List<ExamBody> exams(List<Exam> exams, boolean revealKey) {
        return exams.stream().map(exam -> ExamBody.from(exam, revealKey)).toList();
    }

    private static List<PracticeBody> practices(List<Practice> practices) {
        return practices.stream().map(PracticeBody::from).toList();
    }

    record OpenBody(String name, UUID courseId, String teacher) {
    }

    record PracticeRequest(
            String title,
            String prompt,
            String dueAt,
            java.util.List<skillforge.domain.cohort.CriterionDraft> criteria) {
    }

    record TextRequest(String text) {
    }

    record FeedbackRequest(String student, String text, Integer grade, java.util.List<MarkRequest> marks) {
    }

    record MarkRequest(UUID criterionId, int points) {
    }

    record IdName(UUID id, String name) {
    }

    record EnrollBody(String student) {
    }

    record ProgressBody(String student, int completed, int total) {

        static ProgressBody from(Progress progress) {
            return new ProgressBody(progress.student(), progress.completed(), progress.total());
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LessonBody(UUID id, String title, String kind, Boolean completed) {
    }

    record ModuleBody(UUID id, String name, List<LessonBody> lessons) {
    }

    record CourseBody(UUID id, String name, List<ModuleBody> modules) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CohortBody(
            UUID id,
            String name,
            UUID courseId,
            String courseName,
            String teacher,
            List<ProgressBody> students,
            Integer completed,
            Integer total,
            CourseBody course,
            List<PracticeBody> practices,
            List<ExamBody> exams) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PracticeBody(
            UUID id,
            String title,
            String prompt,
            java.time.Instant dueAt,
            List<CriterionBody> criteria,
            List<DeliveryBody> deliveries) {

        static PracticeBody from(Practice practice) {
            return new PracticeBody(
                    practice.id(),
                    practice.title(),
                    practice.prompt(),
                    practice.dueAt(),
                    practice.criteria().stream()
                            .map(criterion -> new CriterionBody(criterion.id(), criterion.name(), criterion.points()))
                            .toList(),
                    practice.deliveries().stream().map(DeliveryBody::from).toList());
        }

    }

    record CriterionBody(UUID id, String name, int points) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeliveryBody(String student, String text, String feedback, Integer grade) {

        static DeliveryBody from(Delivery delivery) {
            return new DeliveryBody(delivery.student(), delivery.text(), delivery.feedback(), delivery.grade());
        }

    }

    record ReportBody(
            UUID cohortId,
            String cohortName,
            String courseName,
            List<PracticeBody> practices,
            List<ExamBody> exams) {

        static ReportBody from(Report report) {
            return new ReportBody(
                    report.cohortId(),
                    report.cohortName(),
                    report.courseName(),
                    report.practices().stream().map(PracticeBody::from).toList(),
                    report.exams().stream().map(exam -> ExamBody.from(exam, true)).toList());
        }

    }

    record ExamRequest(
            String title,
            String dueAt,
            List<skillforge.domain.cohort.ExamDraft.QuestionDraft> questions) {
    }

    record AttemptRequest(List<AnswerRequest> answers) {
    }

    record NoticeRequest(String title, String body) {
    }

    record CallRequest(java.time.LocalDate heldOn, List<Presence> marks) {
    }

    record AnswerRequest(UUID questionId, UUID choiceId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ExamBody(
            UUID id,
            String title,
            java.time.Instant dueAt,
            List<QuestionBody> questions,
            List<ScoreBody> scores) {

        static ExamBody from(Exam exam, boolean revealKey) {
            return new ExamBody(
                    exam.id(),
                    exam.title(),
                    exam.dueAt(),
                    exam.questions().stream().map(question -> QuestionBody.from(question, revealKey)).toList(),
                    exam.scores().stream().map(ScoreBody::from).toList());
        }

    }

    record QuestionBody(UUID id, String prompt, List<ChoiceBody> choices) {

        static QuestionBody from(Question question, boolean revealKey) {
            return new QuestionBody(
                    question.id(),
                    question.prompt(),
                    question.choices().stream().map(choice -> ChoiceBody.from(choice, revealKey)).toList());
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChoiceBody(UUID id, String label, Boolean correct) {

        static ChoiceBody from(Choice choice, boolean revealKey) {
            return new ChoiceBody(choice.id(), choice.label(), revealKey ? choice.correct() : null);
        }

    }

    record ScoreBody(String student, int grade) {

        static ScoreBody from(ExamScore score) {
            return new ScoreBody(score.student(), score.grade());
        }

    }

}
