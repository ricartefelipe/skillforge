package skillforge.application.studio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.studio.BankQuestion;
import skillforge.domain.studio.Hit;
import skillforge.domain.studio.InboxNote;
import skillforge.domain.studio.Mailbox;
import skillforge.domain.studio.PeerWork;
import skillforge.domain.studio.QuestionText;
import skillforge.domain.studio.Resume;
import skillforge.domain.studio.StudioRepository;
import skillforge.domain.studio.StudyGroup;

public class RecordingStudio implements StudioRepository {

    public String notice;

    public UUID cohortNotice;

    public String savedMail;

    @Override
    public Optional<Resume> resume(Actor actor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void revisePractice(
            UUID cohortId,
            Actor actor,
            UUID practiceId,
            String title,
            String prompt,
            Instant dueAt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reviseExam(
            UUID cohortId,
            Actor actor,
            UUID examId,
            String title,
            Instant dueAt,
            Integer minutes,
            int attemptsAllowed) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reviseAnnouncement(UUID cohortId, Actor actor, UUID announcementId, String title, String body) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void weigh(UUID cohortId, Actor actor, int practiceWeight, int examWeight) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID addBankQuestion(UUID courseId, Actor actor, String prompt, List<ExamDraft.ChoiceDraft> choices) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BankQuestion> bank(UUID courseId, Actor actor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ExamDraft.QuestionDraft> bankDrafts(UUID cohortId, Actor actor, List<UUID> ids) {
        return List.of(new ExamDraft.QuestionDraft("Do banco", List.of(
                new ExamDraft.ChoiceDraft("Certa", true),
                new ExamDraft.ChoiceDraft("Errada", false))));
    }

    @Override
    public UUID openGroup(UUID cohortId, Actor actor, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMember(UUID cohortId, UUID groupId, Actor actor, String student) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<StudyGroup> groups(UUID cohortId, Actor actor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void review(UUID cohortId, UUID practiceId, Actor reviewer, String author, String comment, int mark) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PeerWork> peers(UUID cohortId, UUID practiceId, Actor actor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void tell(String account, String body) {
        notice = body;
    }

    @Override
    public void tellCohort(UUID cohortId, String body) {
        cohortNotice = cohortId;
        notice = body;
    }

    @Override
    public List<InboxNote> inbox(Actor actor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void read(Actor actor, UUID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Hit> search(Actor actor, String query) {
        return List.of(new Hit("aula", query, "/aulas"));
    }

    @Override
    public void saveMail(String account, String email) {
        savedMail = email;
    }

    @Override
    public Mailbox mailbox(String account) {
        return new Mailbox(savedMail, List.of());
    }

    @Override
    public void reviseQuestions(UUID cohortId, Actor actor, UUID examId, List<QuestionText> questions) {
        throw new UnsupportedOperationException();
    }

}
