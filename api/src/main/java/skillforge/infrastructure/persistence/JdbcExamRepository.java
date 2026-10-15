package skillforge.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import skillforge.domain.cohort.Choice;
import skillforge.domain.cohort.Exam;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.cohort.ExamGrade;
import skillforge.domain.cohort.ExamRepository;
import skillforge.domain.cohort.ExamScore;
import skillforge.domain.cohort.Question;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

@Component
class JdbcExamRepository implements ExamRepository {

    private final CohortGate gate;

    JdbcExamRepository(CohortGate gate) {
        this.gate = gate;
    }

    @Override
    @Transactional
    public UUID openExam(UUID cohortId, Actor actor, ExamDraft draft) {
        gate.ensure();
        try {
            UUID examId = UUID.randomUUID();
            gate.jdbc.update(
                    """
                    INSERT INTO exam (id, cohort_id, title, due_at, minutes, attempts_allowed)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    examId,
                    gate.requireLead(cohortId, actor).id(),
                    draft.title(),
                    draft.dueAt() == null ? null : java.sql.Timestamp.from(draft.dueAt()),
                    draft.minutes(),
                    draft.attemptsAllowed());
            int position = 1;
            for (ExamDraft.QuestionDraft question : draft.questions()) {
                UUID questionId = UUID.randomUUID();
                gate.jdbc.update(
                        "INSERT INTO question (id, exam_id, position, prompt) VALUES (?, ?, ?, ?)",
                        questionId,
                        examId,
                        position,
                        question.prompt());
                position++;
                for (ExamDraft.ChoiceDraft choice : question.choices()) {
                    gate.jdbc.update(
                            "INSERT INTO choice (id, question_id, label, correct) VALUES (?, ?, ?, ?)",
                            UUID.randomUUID(),
                            questionId,
                            choice.label(),
                            choice.correct());
                }
            }
            return examId;
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_EXAM);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    @Transactional
    public void takeExam(UUID cohortId, UUID examId, Actor actor, Map<UUID, UUID> answers) {
        gate.ensure();
        try {
            if (!gate.enrolled(cohortId, actor.username()) || !owns(cohortId, examId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            CohortGate.refuseIfClosed(due(examId));
            Exam exam = inCohort(cohortId, actor).stream()
                    .filter(item -> item.id().equals(examId))
                    .findFirst()
                    .orElseThrow(() -> new Rejection(Failure.NOT_FOUND));
            int used = sittings(examId, actor.username());
            if (used >= exam.attemptsAllowed()) {
                throw new Rejection(Failure.ALREADY_TAKEN);
            }
            int ordinal = used + 1;
            int grade = gradeOf(exam, examId, actor.username(), ordinal, answers);
            recordSitting(exam, examId, actor.username(), ordinal, grade, answers);
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.ALREADY_TAKEN);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    @Transactional
    public void startExam(UUID cohortId, UUID examId, Actor actor) {
        gate.ensure();
        try {
            if (!gate.enrolled(cohortId, actor.username()) || !owns(cohortId, examId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            CohortGate.refuseIfClosed(due(examId));
            Integer minutes = gate.jdbc.queryForObject("SELECT minutes FROM exam WHERE id = ?", Integer.class, examId);
            if (minutes == null) {
                return;
            }
            int used = sittings(examId, actor.username());
            Integer allowed = gate.jdbc.queryForObject(
                    "SELECT attempts_allowed FROM exam WHERE id = ?",
                    Integer.class,
                    examId);
            if (used >= (allowed == null ? 1 : allowed)) {
                throw new Rejection(Failure.ALREADY_TAKEN);
            }
            gate.jdbc.update("""
                    INSERT INTO exam_clock (exam_id, student, ordinal, started_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, examId, actor.username(), used + 1, java.sql.Timestamp.from(java.time.Instant.now()));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    @Transactional
    public void removeExam(UUID cohortId, Actor actor, UUID examId) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            if (!owns(cohortId, examId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            gate.jdbc.update("DELETE FROM exam_clock WHERE exam_id = ?", examId);
            gate.jdbc.update("DELETE FROM sitting WHERE exam_id = ?", examId);
            gate.jdbc.update("DELETE FROM attempt_answer WHERE exam_id = ?", examId);
            gate.jdbc.update("DELETE FROM attempt WHERE exam_id = ?", examId);
            gate.jdbc.update(
                    "DELETE FROM choice WHERE question_id IN (SELECT id FROM question WHERE exam_id = ?)",
                    examId);
            gate.jdbc.update("DELETE FROM question WHERE exam_id = ?", examId);
            gate.jdbc.update("DELETE FROM exam WHERE id = ?", examId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<Exam> inCohort(UUID cohortId, Actor actor) {
        gate.ensure();
        List<Seed> exams = gate.jdbc.query(
                "SELECT id, title, due_at, minutes, attempts_allowed FROM exam WHERE cohort_id = ? ORDER BY title",
                (rs, row) -> new Seed(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        CohortGate.instant(rs.getTimestamp("due_at")),
                        (Integer) rs.getObject("minutes"),
                        rs.getObject("attempts_allowed") == null ? 1 : rs.getInt("attempts_allowed")),
                cohortId);
        if (exams.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Row>> questions = new LinkedHashMap<>();
        for (Seed exam : exams) {
            questions.put(exam.id(), new ArrayList<>());
        }
        gate.jdbc.query("""
                SELECT id, exam_id, prompt
                FROM question
                WHERE exam_id IN (SELECT id FROM exam WHERE cohort_id = ?)
                  AND retired IS NOT TRUE
                ORDER BY position
                """, (rs, row) -> {
                    questions.get(rs.getObject("exam_id", UUID.class)).add(new Row(
                            rs.getObject("id", UUID.class),
                            rs.getString("prompt")));
                    return null;
                }, cohortId);
        Map<UUID, List<Choice>> choices = new LinkedHashMap<>();
        gate.jdbc.query("""
                SELECT choice.id, choice.question_id, choice.label, choice.correct
                FROM choice
                JOIN question ON question.id = choice.question_id
                JOIN exam ON exam.id = question.exam_id
                WHERE exam.cohort_id = ?
                  AND choice.retired IS NOT TRUE
                ORDER BY choice.label
                """, (rs, row) -> {
                    choices.computeIfAbsent(rs.getObject("question_id", UUID.class), id -> new ArrayList<>())
                            .add(new Choice(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("label"),
                                    rs.getBoolean("correct")));
                    return null;
                }, cohortId);
        Map<UUID, List<ExamScore>> scores = new LinkedHashMap<>();
        for (Seed exam : exams) {
            scores.put(exam.id(), new ArrayList<>());
        }
        if (actor.role() == Role.ALUNO) {
            gate.jdbc.query("""
                    SELECT exam_id, student, grade FROM attempt
                    WHERE student = ? AND exam_id IN (SELECT id FROM exam WHERE cohort_id = ?)
                    """, (rs, row) -> keepScore(scores, rs), actor.username(), cohortId);
        }
        else {
            gate.jdbc.query("""
                    SELECT exam_id, student, grade FROM attempt
                    WHERE exam_id IN (SELECT id FROM exam WHERE cohort_id = ?)
                    ORDER BY student
                    """, (rs, row) -> keepScore(scores, rs), cohortId);
        }
        List<Exam> assembled = new ArrayList<>();
        for (Seed exam : exams) {
            List<Question> examQuestions = new ArrayList<>();
            for (Row question : questions.get(exam.id())) {
                examQuestions.add(new Question(
                        question.id(),
                        question.prompt(),
                        choices.getOrDefault(question.id(), List.of())));
            }
            int taken = actor.role() == Role.ALUNO ? sittings(exam.id(), actor.username()) : 0;
            assembled.add(new Exam(
                    exam.id(),
                    exam.title(),
                    exam.dueAt(),
                    exam.minutes(),
                    exam.attemptsAllowed(),
                    taken,
                    examQuestions,
                    scores.get(exam.id())));
        }
        return List.copyOf(assembled);
    }

    private boolean owns(UUID cohortId, UUID examId) {
        Integer count = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE id = ? AND cohort_id = ?",
                Integer.class,
                examId,
                cohortId);
        return count != null && count > 0;
    }

    private java.time.Instant due(UUID examId) {
        return CohortGate.instant(gate.jdbc.queryForObject(
                "SELECT due_at FROM exam WHERE id = ?",
                java.sql.Timestamp.class,
                examId));
    }

    private int sittings(UUID examId, String student) {
        Integer count = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM sitting WHERE exam_id = ? AND student = ?",
                Integer.class,
                examId,
                student);
        if (count != null && count > 0) {
            return count;
        }
        Integer legacy = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM attempt WHERE exam_id = ? AND student = ?",
                Integer.class,
                examId,
                student);
        return legacy == null ? 0 : legacy;
    }

    private int gradeOf(Exam exam, UUID examId, String student, int ordinal, Map<UUID, UUID> answers) {
        if (exam.minutes() == null) {
            return ExamGrade.of(exam, answers);
        }
        List<java.sql.Timestamp> clocks = gate.jdbc.query(
                "SELECT started_at FROM exam_clock WHERE exam_id = ? AND student = ? AND ordinal = ?",
                (rs, row) -> rs.getTimestamp("started_at"),
                examId,
                student,
                ordinal);
        if (clocks.isEmpty()) {
            throw new Rejection(Failure.NOT_STARTED);
        }
        java.time.Instant deadline = clocks.get(0).toInstant().plusSeconds(exam.minutes() * 60L);
        if (!java.time.Instant.now().isBefore(deadline)) {
            return 0;
        }
        return ExamGrade.of(exam, answers);
    }

    private void recordSitting(
            Exam exam,
            UUID examId,
            String student,
            int ordinal,
            int grade,
            Map<UUID, UUID> answers) {
        gate.jdbc.update(
                "INSERT INTO sitting (id, exam_id, student, ordinal, grade, started_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                examId,
                student,
                ordinal,
                grade,
                java.sql.Timestamp.from(java.time.Instant.now()));
        List<Integer> previous = gate.jdbc.query(
                "SELECT grade FROM attempt WHERE exam_id = ? AND student = ?",
                (rs, row) -> rs.getInt("grade"),
                examId,
                student);
        boolean keepAnswers = grade > 0 || exam.minutes() == null;
        if (previous.isEmpty()) {
            gate.jdbc.update("INSERT INTO attempt (exam_id, student, grade) VALUES (?, ?, ?)", examId, student, grade);
            if (keepAnswers) {
                writeAnswers(examId, student, answers);
            }
            return;
        }
        if (grade < previous.get(0)) {
            return;
        }
        gate.jdbc.update(
                "UPDATE attempt SET grade = ? WHERE exam_id = ? AND student = ?",
                grade,
                examId,
                student);
        if (keepAnswers) {
            gate.jdbc.update("DELETE FROM attempt_answer WHERE exam_id = ? AND student = ?", examId, student);
            writeAnswers(examId, student, answers);
        }
    }

    private void writeAnswers(UUID examId, String student, Map<UUID, UUID> answers) {
        for (Map.Entry<UUID, UUID> answer : answers.entrySet()) {
            gate.jdbc.update(
                    "INSERT INTO attempt_answer (exam_id, student, question_id, choice_id) VALUES (?, ?, ?, ?)",
                    examId,
                    student,
                    answer.getKey(),
                    answer.getValue());
        }
    }

    private static Object keepScore(Map<UUID, List<ExamScore>> scores, ResultSet rs) throws SQLException {
        List<ExamScore> found = scores.get(rs.getObject("exam_id", UUID.class));
        if (found != null) {
            found.add(new ExamScore(rs.getString("student"), rs.getInt("grade")));
        }
        return null;
    }

    private record Seed(UUID id, String title, java.time.Instant dueAt, Integer minutes, int attemptsAllowed) {
    }

    private record Row(UUID id, String prompt) {
    }

}
