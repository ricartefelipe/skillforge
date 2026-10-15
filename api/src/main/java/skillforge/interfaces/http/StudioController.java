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

import skillforge.application.studio.StudioApplication;
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

    private final StudioApplication studio;

    StudioController(StudioApplication studio) {
        this.studio = studio;
    }

    @GetMapping("/search")
    java.util.List<Hit> search(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String query) {
        return studio.search(actor(authentication), query);
    }

    @GetMapping("/me/email")
    java.util.List<String> mail(Authentication authentication) {
        return studio.mail(actor(authentication));
    }

    @PutMapping("/me/email")
    ResponseEntity<Void> saveMail(Authentication authentication, @RequestBody MailEdit body) {
        MailEdit request = body == null ? new MailEdit(null) : body;
        studio.saveMail(actor(authentication), request.email());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/exams/{examId}/questions")
    ResponseEntity<Void> reviseQuestions(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID examId,
            @RequestBody QuestionEdit body) {
        studio.reviseQuestions(
                actor(authentication),
                id,
                examId,
                body == null || body.questions() == null ? java.util.List.of() : body.questions());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resume")
    ResponseEntity<ResumeBody> resume(Authentication authentication) {
        return studio.resume(actor(authentication))
                .map(item -> ResponseEntity.ok(ResumeBody.from(item)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/inbox")
    List<InboxNote> inbox(Authentication authentication) {
        return studio.inbox(actor(authentication));
    }

    @PostMapping("/inbox/{id}/read")
    ResponseEntity<Void> read(Authentication authentication, @PathVariable UUID id) {
        studio.read(actor(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/practices/{practiceId}")
    ResponseEntity<Void> revisePractice(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID practiceId,
            @RequestBody PracticeEdit body) {
        PracticeEdit request = body == null ? new PracticeEdit(null, null, null) : body;
        studio.revisePractice(
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
        studio.reviseExam(
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
        studio.reviseAnnouncement(actor(authentication), id, announcementId, request.title(), request.body());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cohorts/{id}/weights")
    ResponseEntity<Void> weigh(Authentication authentication, @PathVariable UUID id, @RequestBody WeightEdit body) {
        WeightEdit request = body == null ? new WeightEdit(0, 0) : body;
        studio.weigh(actor(authentication), id, request.practiceWeight(), request.examWeight());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses/{courseId}/bank")
    List<BankQuestion> bank(Authentication authentication, @PathVariable UUID courseId) {
        return studio.bank(actor(authentication), courseId);
    }

    @PostMapping("/courses/{courseId}/bank")
    ResponseEntity<IdBody> addBankQuestion(
            Authentication authentication,
            @PathVariable UUID courseId,
            @RequestBody BankEdit body) {
        BankEdit request = body == null ? new BankEdit(null, null) : body;
        UUID id = studio.addBankQuestion(actor(authentication), courseId, request.prompt(), request.choices());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdBody(id));
    }

    @GetMapping("/cohorts/{id}/groups")
    List<StudyGroup> groups(Authentication authentication, @PathVariable UUID id) {
        return studio.groups(actor(authentication), id);
    }

    @PostMapping("/cohorts/{id}/groups")
    ResponseEntity<IdBody> openGroup(Authentication authentication, @PathVariable UUID id, @RequestBody NameEdit body) {
        NameEdit request = body == null ? new NameEdit(null) : body;
        UUID created = studio.openGroup(actor(authentication), id, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdBody(created));
    }

    @PostMapping("/cohorts/{id}/groups/{groupId}/members")
    ResponseEntity<Void> addMember(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @RequestBody MemberEdit body) {
        MemberEdit request = body == null ? new MemberEdit(null) : body;
        studio.addMember(actor(authentication), id, groupId, request.student());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/cohorts/{id}/practices/{practiceId}/reviews")
    List<PeerWork> peers(Authentication authentication, @PathVariable UUID id, @PathVariable UUID practiceId) {
        return studio.peers(actor(authentication), id, practiceId);
    }

    @PostMapping("/cohorts/{id}/practices/{practiceId}/reviews")
    ResponseEntity<Void> review(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID practiceId,
            @RequestBody ReviewEdit body) {
        ReviewEdit request = body == null ? new ReviewEdit(null, null, 0) : body;
        studio.review(actor(authentication), id, practiceId, request.author(), request.comment(), request.mark());
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
