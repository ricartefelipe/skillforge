package skillforge.interfaces.http;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import skillforge.application.bank.BankApplication;
import skillforge.application.cohort.ClassroomApplication;
import skillforge.application.cohort.CohortApplication;
import skillforge.application.cohort.ExamApplication;
import skillforge.application.cohort.PracticeApplication;
import skillforge.application.group.GroupApplication;
import skillforge.application.mail.MailApplication;
import skillforge.application.notice.NoticeApplication;
import skillforge.application.resume.ResumeApplication;
import skillforge.application.search.SearchApplication;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.studio.BankQuestion;
import skillforge.domain.studio.Hit;
import skillforge.domain.studio.QuestionText;
import skillforge.domain.studio.InboxNote;
import skillforge.domain.studio.PeerWork;
import skillforge.domain.studio.Resume;
import skillforge.domain.studio.StudyGroup;

@RestController
class StudioController {

    private final SearchApplication search;

    private final MailApplication mail;

    private final ExamApplication exams;

    private final ResumeApplication resumes;

    private final NoticeApplication notices;

    private final PracticeApplication practices;

    private final ClassroomApplication classroom;

    private final CohortApplication cohorts;

    private final BankApplication bank;

    private final GroupApplication groups;

    StudioController(
            SearchApplication search,
            MailApplication mail,
            ExamApplication exams,
            ResumeApplication resumes,
            NoticeApplication notices,
            PracticeApplication practices,
            ClassroomApplication classroom,
            CohortApplication cohorts,
            BankApplication bank,
            GroupApplication groups) {
        this.search = search;
        this.mail = mail;
        this.exams = exams;
        this.resumes = resumes;
        this.notices = notices;
        this.practices = practices;
        this.classroom = classroom;
        this.cohorts = cohorts;
        this.bank = bank;
        this.groups = groups;
    }

    @GetMapping("/search")
    java.util.List<Hit> search(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String query) {
        return search.search(actor(authentication), query);
    }

    @GetMapping("/me/email")
    skillforge.domain.studio.Mailbox mail(Authentication authentication) {
        return mail.mailbox(actor(authentication));
    }

    @PutMapping("/me/email")
    ResponseEntity<Void> saveMail(Authentication authentication, @RequestBody MailEdit body) {
        MailEdit request = body == null ? new MailEdit(null) : body;
        mail.save(actor(authentication), request.email());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/exams/{examId}/questions")
    ResponseEntity<Void> reviseQuestions(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID examId,
            @RequestBody QuestionEdit body) {
        exams.reviseQuestions(
                actor(authentication),
                id,
                examId,
                body == null || body.questions() == null ? java.util.List.of() : body.questions());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resume")
    ResponseEntity<ResumeBody> resume(Authentication authentication) {
        return resumes.resume(actor(authentication))
                .map(item -> ResponseEntity.ok(ResumeBody.from(item)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/inbox")
    List<InboxNote> inbox(Authentication authentication) {
        return notices.inbox(actor(authentication));
    }

    @PostMapping("/inbox/{id}/read")
    ResponseEntity<Void> read(Authentication authentication, @PathVariable UUID id) {
        notices.read(actor(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/practices/{practiceId}")
    ResponseEntity<Void> revisePractice(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID practiceId,
            @RequestBody PracticeEdit body) {
        PracticeEdit request = body == null ? new PracticeEdit(null, null, null) : body;
        practices.revise(
                actor(authentication),
                id,
                practiceId,
                request.title(),
                request.prompt(),
                instant(request.dueAt()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/exams/{examId}")
    ResponseEntity<Void> reviseExam(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID examId,
            @RequestBody ExamEdit body) {
        ExamEdit request = body == null ? new ExamEdit(null, null, null, null) : body;
        exams.revise(
                actor(authentication),
                id,
                examId,
                request.title(),
                instant(request.dueAt()),
                request.minutes(),
                request.attemptsAllowed());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/announcements/{announcementId}")
    ResponseEntity<Void> reviseAnnouncement(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID announcementId,
            @RequestBody NoticeEdit body) {
        NoticeEdit request = body == null ? new NoticeEdit(null, null) : body;
        classroom.reviseAnnouncement(actor(authentication), id, announcementId, request.title(), request.body());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/weights")
    ResponseEntity<Void> weigh(Authentication authentication, @PathVariable UUID id, @RequestBody WeightEdit body) {
        WeightEdit request = body == null ? new WeightEdit(0, 0) : body;
        cohorts.weigh(actor(authentication), id, request.practiceWeight(), request.examWeight());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses/{courseId}/bank")
    List<BankQuestion> bank(Authentication authentication, @PathVariable UUID courseId) {
        return bank.list(actor(authentication), courseId);
    }

    @PostMapping("/courses/{courseId}/bank")
    ResponseEntity<IdBody> addBankQuestion(
            Authentication authentication,
            @PathVariable UUID courseId,
            @RequestBody BankEdit body) {
        BankEdit request = body == null ? new BankEdit(null, null) : body;
        UUID id = bank.add(actor(authentication), courseId, request.prompt(), request.choices());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdBody(id));
    }

    @GetMapping("/cohorts/{id}/groups")
    List<StudyGroup> groups(Authentication authentication, @PathVariable UUID id) {
        return groups.groups(actor(authentication), id);
    }

    @PostMapping("/cohorts/{id}/groups")
    ResponseEntity<IdBody> openGroup(Authentication authentication, @PathVariable UUID id, @RequestBody NameEdit body) {
        NameEdit request = body == null ? new NameEdit(null) : body;
        UUID created = groups.open(actor(authentication), id, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdBody(created));
    }

    @PostMapping("/cohorts/{id}/groups/{groupId}/members")
    ResponseEntity<Void> addMember(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @RequestBody MemberEdit body) {
        MemberEdit request = body == null ? new MemberEdit(null) : body;
        groups.addMember(actor(authentication), id, groupId, request.student());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/cohorts/{id}/practices/{practiceId}/reviews")
    List<PeerWork> peers(Authentication authentication, @PathVariable UUID id, @PathVariable UUID practiceId) {
        return groups.peers(actor(authentication), id, practiceId);
    }

    @PostMapping("/cohorts/{id}/practices/{practiceId}/reviews")
    ResponseEntity<Void> review(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID practiceId,
            @RequestBody ReviewEdit body) {
        ReviewEdit request = body == null ? new ReviewEdit(null, null, 0) : body;
        groups.review(actor(authentication), id, practiceId, request.author(), request.comment(), request.mark());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static Actor actor(Authentication authentication) {
        return RequestActor.from(authentication);
    }

    private static Instant instant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Instant.parse(raw);
    }

    record ResumeBody(UUID lessonId, String title) {

        static ResumeBody from(Resume resume) {
            return new ResumeBody(resume.lessonId(), resume.title());
        }

    }

    record IdBody(UUID id) {
    }

    record PracticeEdit(String title, String prompt, String dueAt) {
    }

    record ExamEdit(String title, String dueAt, Integer minutes, Integer attemptsAllowed) {
    }

    record NoticeEdit(String title, String body) {
    }

    record WeightEdit(int practiceWeight, int examWeight) {
    }

    record BankEdit(String prompt, List<ExamDraft.ChoiceDraft> choices) {
    }

    record NameEdit(String name) {
    }

    record MemberEdit(String student) {
    }

    record ReviewEdit(String author, String comment, int mark) {
    }

    record MailEdit(String email) {
    }

    record QuestionEdit(java.util.List<QuestionText> questions) {
    }

}
