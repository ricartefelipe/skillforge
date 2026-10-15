package skillforge.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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
import skillforge.domain.studio.QuestionText;
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

    @Override
    public void revise(
            UUID cohortId,
            Actor actor,
            UUID examId,
            String title,
            Instant dueAt,
            Integer minutes,
            int attemptsAllowed) {
        gate.ensure();
        try {
            gate.requireLead(cohortId, actor);
            if (!examOwned(cohortId, examId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer taken = gate.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM attempt WHERE exam_id = ?",
                    Integer.class,
                    examId);
            if (taken != null && taken > 0) {
                throw new Rejection(Failure.LOCKED);
            }
            gate.jdbc.update(
                    "UPDATE exam SET title = ?, due_at = ?, minutes = ?, attempts_allowed = ? WHERE id = ?",
                    title,
                    dueAt == null ? null : Timestamp.from(dueAt),
                    minutes,
                    attemptsAllowed,
                    examId);
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
    public void reviseQuestions(UUID cohortId, Actor actor, UUID examId, List<QuestionText> questions) {
        gate.ensure();
        gate.requireLead(cohortId, actor);
        if (!examOwned(cohortId, examId)) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        Set<UUID> kept = new HashSet<>();
        for (QuestionText question : questions) {
            UUID questionId = question.id() == null ? UUID.randomUUID() : question.id();
            if (question.id() == null) {
                Integer position = gate.jdbc.queryForObject(
                        "SELECT COALESCE(MAX(position), 0) + 1 FROM question WHERE exam_id = ?",
                        Integer.class,
                        examId);
                gate.jdbc.update(
                        "INSERT INTO question (id, exam_id, position, prompt, retired) VALUES (?, ?, ?, ?, false)",
                        questionId,
                        examId,
                        position,
                        question.prompt());
            }
            else {
                int updated = gate.jdbc.update(
                        "UPDATE question SET prompt = ?, retired = false WHERE id = ? AND exam_id = ?",
                        question.prompt(),
                        questionId,
                        examId);
                if (updated == 0) {
                    throw new Rejection(Failure.NOT_FOUND);
                }
            }
            kept.add(questionId);
            keepChoices(question, questionId);
        }
        gate.jdbc.update("UPDATE question SET retired = true WHERE exam_id = ?", examId);
        for (UUID questionId : kept) {
            gate.jdbc.update("UPDATE question SET retired = false WHERE id = ?", questionId);
        }
    }

    private void keepChoices(QuestionText question, UUID questionId) {
        boolean first = question.id() == null;
        Set<UUID> keptChoices = new HashSet<>();
        for (QuestionText.ChoiceText choice : question.choices()) {
            if (choice.id() == null) {
                UUID choiceId = UUID.randomUUID();
                gate.jdbc.update(
                        "INSERT INTO choice (id, question_id, label, correct, retired) VALUES (?, ?, ?, ?, false)",
                        choiceId,
                        questionId,
                        choice.label(),
                        first);
                first = false;
                keptChoices.add(choiceId);
            }
            else {
                int label = gate.jdbc.update(
                        "UPDATE choice SET label = ?, retired = false WHERE id = ? AND question_id = ?",
                        choice.label(),
                        choice.id(),
                        questionId);
                if (label == 0) {
                    throw new Rejection(Failure.NOT_FOUND);
                }
                keptChoices.add(choice.id());
            }
        }
        gate.jdbc.update("UPDATE choice SET retired = true WHERE question_id = ?", questionId);
        for (UUID choiceId : keptChoices) {
            gate.jdbc.update("UPDATE choice SET retired = false WHERE id = ?", choiceId);
        }
        Integer correct = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM choice WHERE question_id = ? AND correct AND retired IS NOT TRUE",
                Integer.class,
                questionId);
        if (correct == null || correct != 1) {
            throw new Rejection(Failure.INVALID_EXAM);
        }
    }

    private boolean examOwned(UUID cohortId, UUID examId) {
        Integer count = gate.jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE id = ? AND cohort_id = ?",
                Integer.class,
                examId,
                cohortId);
        return count != null && count > 0;
    }

}
