package skillforge.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import skillforge.domain.bank.BankRepository;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.BankQuestion;

@Component
class JdbcBankRepository implements BankRepository {

    private final CohortGate gate;

    JdbcBankRepository(CohortGate gate) {
        this.gate = gate;
    }

    @Override
    @Transactional
    public UUID add(UUID courseId, Actor actor, String prompt, List<ExamDraft.ChoiceDraft> choices) {
        gate.ensure();
        try {
            requireCourseLead(courseId, actor);
            UUID questionId = UUID.randomUUID();
            gate.jdbc.update(
                    "INSERT INTO bank_question (id, course_id, prompt) VALUES (?, ?, ?)",
                    questionId,
                    courseId,
                    prompt);
            for (ExamDraft.ChoiceDraft choice : choices) {
                gate.jdbc.update(
                        "INSERT INTO bank_choice (id, question_id, label, correct) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(),
                        questionId,
                        choice.label(),
                        choice.correct());
            }
            return questionId;
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<BankQuestion> list(UUID courseId, Actor actor) {
        gate.ensure();
        try {
            requireCourseLead(courseId, actor);
            return questionsOf(courseId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<ExamDraft.QuestionDraft> drafts(UUID cohortId, Actor actor, List<UUID> ids) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            UUID courseId = courseOf(cohortId);
            List<ExamDraft.QuestionDraft> drafts = new ArrayList<>();
            for (UUID id : ids) {
                List<BankQuestion> found = questionsOf(courseId).stream()
                        .filter(question -> question.id().equals(id))
                        .toList();
                if (found.isEmpty()) {
                    throw new Rejection(Failure.NOT_FOUND);
                }
                BankQuestion question = found.get(0);
                drafts.add(new ExamDraft.QuestionDraft(question.prompt(), question.choices()));
            }
            return List.copyOf(drafts);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private void requireCourseLead(UUID courseId, Actor actor) {
        Integer courses = gate.jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = ?", Integer.class, courseId);
        if (courses == null || courses == 0) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        if (actor.role() == Role.ADMINISTRACAO) {
            return;
        }
        if (actor.role() != Role.DOCENTE) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        Integer taught = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM cohort WHERE course_id = ? AND teacher = ?",
                Integer.class,
                courseId,
                actor.username());
        if (taught == null || taught == 0) {
            throw new Rejection(Failure.NOT_FOUND);
        }
    }

    private UUID courseOf(UUID cohortId) {
        List<UUID> ids = gate.jdbc.query(
                "SELECT course_id FROM cohort WHERE id = ?",
                (rs, row) -> rs.getObject("course_id", UUID.class),
                cohortId);
        if (ids.isEmpty()) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return ids.get(0);
    }

    private List<BankQuestion> questionsOf(UUID courseId) {
        List<UUID> ids = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        gate.jdbc.query(
                "SELECT id, prompt FROM bank_question WHERE course_id = ? ORDER BY prompt",
                (rs, row) -> {
                    ids.add(rs.getObject("id", UUID.class));
                    prompts.add(rs.getString("prompt"));
                    return null;
                },
                courseId);
        List<BankQuestion> questions = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            UUID id = ids.get(index);
            List<ExamDraft.ChoiceDraft> choices = gate.jdbc.query(
                    "SELECT label, correct FROM bank_choice WHERE question_id = ? ORDER BY label",
                    (rs, row) -> new ExamDraft.ChoiceDraft(rs.getString("label"), rs.getBoolean("correct")),
                    id);
            questions.add(new BankQuestion(id, prompts.get(index), choices));
        }
        return questions;
    }

}
