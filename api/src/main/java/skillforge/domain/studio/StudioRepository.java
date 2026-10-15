package skillforge.domain.studio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;

public interface StudioRepository {

    Optional<Resume> resume(Actor actor);

    void revisePractice(
            UUID cohortId,
            Actor actor,
            UUID practiceId,
            String title,
            String prompt,
            Instant dueAt);

    void reviseExam(
            UUID cohortId,
            Actor actor,
            UUID examId,
            String title,
            Instant dueAt,
            Integer minutes,
            int attemptsAllowed);

    void reviseAnnouncement(UUID cohortId, Actor actor, UUID announcementId, String title, String body);

    void weigh(UUID cohortId, Actor actor, int practiceWeight, int examWeight);

    UUID addBankQuestion(UUID courseId, Actor actor, String prompt, List<ExamDraft.ChoiceDraft> choices);

    List<BankQuestion> bank(UUID courseId, Actor actor);

    List<ExamDraft.QuestionDraft> bankDrafts(UUID cohortId, Actor actor, List<UUID> ids);

    UUID openGroup(UUID cohortId, Actor actor, String name);

    void addMember(UUID cohortId, UUID groupId, Actor actor, String student);

    List<StudyGroup> groups(UUID cohortId, Actor actor);

    void review(UUID cohortId, UUID practiceId, Actor reviewer, String author, String comment, int mark);

    List<PeerWork> peers(UUID cohortId, UUID practiceId, Actor actor);

    void tell(String account, String body);

    void tellCohort(UUID cohortId, String body);

    List<InboxNote> inbox(Actor actor);

    void read(Actor actor, UUID id);

    java.util.List<Hit> search(Actor actor, String query);

    void saveMail(String account, String email);

    java.util.List<String> mail(String account);

    void reviseQuestions(UUID cohortId, Actor actor, UUID examId, java.util.List<QuestionText> questions);

}
