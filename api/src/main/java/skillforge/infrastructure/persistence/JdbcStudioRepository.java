package skillforge.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import skillforge.domain.cohort.CohortAccess;
import skillforge.domain.cohort.ExamDraft;
import skillforge.domain.identity.Actor;
import skillforge.domain.identity.Role;
import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;
import skillforge.domain.studio.BankQuestion;
import skillforge.domain.studio.InboxNote;
import skillforge.domain.studio.PeerNote;
import skillforge.domain.studio.PeerWork;
import skillforge.domain.studio.Resume;
import skillforge.domain.studio.StudioRepository;
import skillforge.domain.studio.StudyGroup;

@Component
class JdbcStudioRepository implements StudioRepository {

    private final JdbcTemplate jdbc;

    private final Schema schema;

    JdbcStudioRepository(JdbcTemplate jdbc, Schema schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @Override
    public Optional<Resume> resume(Actor actor) {
        schema.ensure();
        try {
            List<Resume> rows = switch (actor.role()) {
                case ALUNO -> nextForStudent(actor.username());
                case DOCENTE -> nextForTeacher(actor.username());
                case ADMINISTRACAO -> nextInCatalog();
            };
            return rows.stream().findFirst();
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void revisePractice(
            UUID cohortId,
            Actor actor,
            UUID practiceId,
            String title,
            String prompt,
            Instant dueAt) {
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            if (!practiceInCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer submissions = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM submission WHERE practice_id = ?",
                    Integer.class,
                    practiceId);
            if (submissions != null && submissions > 0) {
                throw new Rejection(Failure.LOCKED);
            }
            jdbc.update(
                    "UPDATE practice SET title = ?, prompt = ?, due_at = ? WHERE id = ?",
                    title,
                    prompt,
                    dueAt == null ? null : Timestamp.from(dueAt),
                    practiceId);
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_PRACTICE);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
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
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            if (!examInCohort(cohortId, examId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer taken = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM attempt WHERE exam_id = ?",
                    Integer.class,
                    examId);
            if (taken != null && taken > 0) {
                throw new Rejection(Failure.LOCKED);
            }
            jdbc.update(
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
    public void reviseAnnouncement(
            UUID cohortId,
            Actor actor,
            UUID announcementId,
            String title,
            String body) {
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            int updated = jdbc.update(
                    "UPDATE announcement SET title = ?, body = ? WHERE id = ? AND cohort_id = ?",
                    title,
                    body,
                    announcementId,
                    cohortId);
            if (updated == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void weigh(UUID cohortId, Actor actor, int practiceWeight, int examWeight) {
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            jdbc.update(
                    "UPDATE cohort SET practice_weight = ?, exam_weight = ? WHERE id = ?",
                    practiceWeight,
                    examWeight,
                    cohortId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    @Transactional
    public UUID addBankQuestion(UUID courseId, Actor actor, String prompt, List<ExamDraft.ChoiceDraft> choices) {
        schema.ensure();
        try {
            requireCourseLead(courseId, actor);
            UUID questionId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO bank_question (id, course_id, prompt) VALUES (?, ?, ?)",
                    questionId,
                    courseId,
                    prompt);
            for (ExamDraft.ChoiceDraft choice : choices) {
                jdbc.update(
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
    public List<BankQuestion> bank(UUID courseId, Actor actor) {
        schema.ensure();
        try {
            requireCourseLead(courseId, actor);
            return questionsOf(courseId);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<ExamDraft.QuestionDraft> bankDrafts(UUID cohortId, Actor actor, List<UUID> ids) {
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            UUID courseId = courseOfCohort(cohortId);
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

    @Override
    public UUID openGroup(UUID cohortId, Actor actor, String name) {
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO study_group (id, cohort_id, name) VALUES (?, ?, ?)",
                    id,
                    cohortId,
                    name);
            return id;
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.DUPLICATE_GROUP);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void addMember(UUID cohortId, UUID groupId, Actor actor, String student) {
        schema.ensure();
        try {
            requireLead(cohortId, actor);
            if (!groupInCohort(cohortId, groupId) || !enrolled(cohortId, student)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer elsewhere = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM study_member
                    JOIN study_group ON study_group.id = study_member.group_id
                    WHERE study_group.cohort_id = ? AND study_member.student = ?
                    """, Integer.class, cohortId, student);
            if (elsewhere != null && elsewhere > 0) {
                throw new Rejection(Failure.GROUPED);
            }
            jdbc.update("INSERT INTO study_member (group_id, student) VALUES (?, ?)", groupId, student);
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.GROUPED);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<StudyGroup> groups(UUID cohortId, Actor actor) {
        schema.ensure();
        try {
            requireSeen(cohortId, actor);
            List<StudyGroup> groups = new ArrayList<>();
            List<UUID> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            jdbc.query(
                    "SELECT id, name FROM study_group WHERE cohort_id = ? ORDER BY name",
                    (rs, row) -> {
                        ids.add(rs.getObject("id", UUID.class));
                        names.add(rs.getString("name"));
                        return null;
                    },
                    cohortId);
            for (int index = 0; index < ids.size(); index++) {
                UUID id = ids.get(index);
                List<String> members = jdbc.query(
                        "SELECT student FROM study_member WHERE group_id = ? ORDER BY student",
                        (rs, row) -> rs.getString("student"),
                        id);
                groups.add(new StudyGroup(id, names.get(index), members));
            }
            return List.copyOf(groups);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void review(
            UUID cohortId,
            UUID practiceId,
            Actor reviewer,
            String author,
            String comment,
            int mark) {
        schema.ensure();
        try {
            reviewer.requireStudent();
            if (!enrolled(cohortId, reviewer.username()) || !practiceInCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            if (reviewer.username().equals(author) || !sameGroup(cohortId, reviewer.username(), author)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Integer submitted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM submission WHERE practice_id = ? AND student = ?",
                    Integer.class,
                    practiceId,
                    author);
            if (submitted == null || submitted == 0) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            jdbc.update(
                    "INSERT INTO peer_review (practice_id, author, reviewer, comment, mark) VALUES (?, ?, ?, ?, ?)",
                    practiceId,
                    author,
                    reviewer.username(),
                    comment,
                    mark);
            tell(author, "Um colega avaliou a sua entrega.");
        }
        catch (DuplicateKeyException ex) {
            throw new Rejection(Failure.ALREADY_REVIEWED);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<PeerWork> peers(UUID cohortId, UUID practiceId, Actor actor) {
        schema.ensure();
        try {
            requireSeen(cohortId, actor);
            if (!practiceInCohort(cohortId, practiceId)) {
                throw new Rejection(Failure.NOT_FOUND);
            }
            Map<String, String> texts = new LinkedHashMap<>();
            if (actor.role() == Role.ALUNO) {
                List<String> peers = classmates(cohortId, actor.username());
                if (peers.isEmpty()) {
                    return List.of();
                }
                jdbc.query(
                        "SELECT student, body FROM submission WHERE practice_id = ? ORDER BY student",
                        (rs, row) -> {
                            String student = rs.getString("student");
                            if (peers.contains(student)) {
                                texts.put(student, rs.getString("body"));
                            }
                            return null;
                        },
                        practiceId);
            }
            else {
                jdbc.query(
                        "SELECT student, body FROM submission WHERE practice_id = ? ORDER BY student",
                        (rs, row) -> {
                            texts.put(rs.getString("student"), rs.getString("body"));
                            return null;
                        },
                        practiceId);
            }
            Map<String, List<PeerNote>> notes = notesOf(practiceId, actor);
            List<PeerWork> works = new ArrayList<>();
            for (Map.Entry<String, String> entry : texts.entrySet()) {
                works.add(new PeerWork(
                        entry.getKey(),
                        entry.getValue(),
                        notes.getOrDefault(entry.getKey(), List.of())));
            }
            return List.copyOf(works);
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void tell(String account, String body) {
        schema.ensure();
        try {
            jdbc.update(
                    "INSERT INTO inbox (id, account, body, created_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    account,
                    body,
                    Timestamp.from(Instant.now()));
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void tellCohort(UUID cohortId, String body) {
        schema.ensure();
        try {
            List<String> accounts = jdbc.query(
                    "SELECT student FROM enrollment WHERE cohort_id = ?",
                    (rs, row) -> rs.getString("student"),
                    cohortId);
            List<String> teachers = jdbc.query(
                    "SELECT teacher FROM cohort WHERE id = ?",
                    (rs, row) -> rs.getString("teacher"),
                    cohortId);
            accounts.addAll(teachers);
            for (String account : accounts) {
                tell(account, body);
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public List<InboxNote> inbox(Actor actor) {
        schema.ensure();
        try {
            return jdbc.query("""
                    SELECT id, body, created_at, read_at
                    FROM inbox
                    WHERE account = ?
                    ORDER BY created_at DESC
                    """, (rs, row) -> new InboxNote(
                    rs.getObject("id", UUID.class),
                    rs.getString("body"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("read_at") != null), actor.username());
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    @Override
    public void read(Actor actor, UUID id) {
        schema.ensure();
        try {
            int updated = jdbc.update(
                    "UPDATE inbox SET read_at = ? WHERE id = ? AND account = ? AND read_at IS NULL",
                    Timestamp.from(Instant.now()),
                    id,
                    actor.username());
            if (updated == 0) {
                Integer own = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inbox WHERE id = ? AND account = ?",
                        Integer.class,
                        id,
                        actor.username());
                if (own == null || own == 0) {
                    throw new Rejection(Failure.NOT_FOUND);
                }
            }
        }
        catch (DataAccessResourceFailureException ex) {
            throw new Unavailable(ex);
        }
    }

    private List<Resume> nextForStudent(String student) {
        return jdbc.query("""
                SELECT lesson.id, lesson.title
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                JOIN course ON course.id = module.course_id
                JOIN cohort ON cohort.course_id = course.id
                JOIN enrollment ON enrollment.cohort_id = cohort.id AND enrollment.student = ?
                LEFT JOIN completion ON completion.lesson_id = lesson.id
                    AND completion.cohort_id = cohort.id
                    AND completion.student = ?
                WHERE completion.lesson_id IS NULL
                ORDER BY course.name, module.position, lesson.position
                LIMIT 1
                """, (rs, row) -> new Resume(rs.getObject("id", UUID.class), rs.getString("title")), student, student);
    }

    private List<Resume> nextForTeacher(String teacher) {
        return jdbc.query("""
                SELECT lesson.id, lesson.title
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                JOIN cohort ON cohort.course_id = module.course_id AND cohort.teacher = ?
                ORDER BY module.position, lesson.position
                LIMIT 1
                """, (rs, row) -> new Resume(rs.getObject("id", UUID.class), rs.getString("title")), teacher);
    }

    private List<Resume> nextInCatalog() {
        return jdbc.query("""
                SELECT lesson.id, lesson.title
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                ORDER BY module.position, lesson.position
                LIMIT 1
                """, (rs, row) -> new Resume(rs.getObject("id", UUID.class), rs.getString("title")));
    }

    private List<BankQuestion> questionsOf(UUID courseId) {
        List<UUID> ids = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        jdbc.query(
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
            List<ExamDraft.ChoiceDraft> choices = jdbc.query(
                    "SELECT label, correct FROM bank_choice WHERE question_id = ? ORDER BY label",
                    (rs, row) -> new ExamDraft.ChoiceDraft(rs.getString("label"), rs.getBoolean("correct")),
                    id);
            questions.add(new BankQuestion(id, prompts.get(index), choices));
        }
        return questions;
    }

    private Map<String, List<PeerNote>> notesOf(UUID practiceId, Actor actor) {
        Map<String, List<PeerNote>> notes = new LinkedHashMap<>();
        jdbc.query(
                "SELECT author, reviewer, comment, mark FROM peer_review WHERE practice_id = ? ORDER BY reviewer",
                (rs, row) -> {
                    String author = rs.getString("author");
                    String reviewer = rs.getString("reviewer");
                    boolean visible = actor.role() != Role.ALUNO
                            || reviewer.equals(actor.username())
                            || author.equals(actor.username());
                    if (visible) {
                        notes.computeIfAbsent(author, key -> new ArrayList<>()).add(new PeerNote(
                                author,
                                reviewer,
                                rs.getString("comment"),
                                rs.getInt("mark")));
                    }
                    return null;
                },
                practiceId);
        return notes;
    }

    private List<String> classmates(UUID cohortId, String student) {
        return jdbc.query("""
                SELECT theirs.student
                FROM study_member mine
                JOIN study_member theirs ON theirs.group_id = mine.group_id
                JOIN study_group ON study_group.id = mine.group_id
                WHERE study_group.cohort_id = ? AND mine.student = ? AND theirs.student <> ?
                """, (rs, row) -> rs.getString("student"), cohortId, student, student);
    }

    private boolean sameGroup(UUID cohortId, String reviewer, String author) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM study_member mine
                JOIN study_member theirs ON theirs.group_id = mine.group_id
                JOIN study_group ON study_group.id = mine.group_id
                WHERE study_group.cohort_id = ? AND mine.student = ? AND theirs.student = ?
                """, Integer.class, cohortId, reviewer, author);
        return count != null && count > 0;
    }

    private void requireLead(UUID cohortId, Actor actor) {
        String teacher = teacherOf(cohortId);
        if (!CohortAccess.canLead(actor.role(), teacher, actor.username())) {
            throw new Rejection(Failure.NOT_FOUND);
        }
    }

    private void requireSeen(UUID cohortId, Actor actor) {
        String teacher = teacherOf(cohortId);
        if (!CohortAccess.canSee(actor.role(), teacher, actor.username(), enrolled(cohortId, actor.username()))) {
            throw new Rejection(Failure.NOT_FOUND);
        }
    }

    private void requireCourseLead(UUID courseId, Actor actor) {
        Integer courses = jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = ?", Integer.class, courseId);
        if (courses == null || courses == 0) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        if (actor.role() == Role.ADMINISTRACAO) {
            return;
        }
        if (actor.role() != Role.DOCENTE) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        Integer taught = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cohort WHERE course_id = ? AND teacher = ?",
                Integer.class,
                courseId,
                actor.username());
        if (taught == null || taught == 0) {
            throw new Rejection(Failure.NOT_FOUND);
        }
    }

    private String teacherOf(UUID cohortId) {
        List<String> teachers = jdbc.query(
                "SELECT teacher FROM cohort WHERE id = ?",
                (rs, row) -> rs.getString("teacher"),
                cohortId);
        if (teachers.isEmpty()) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return teachers.get(0);
    }

    private UUID courseOfCohort(UUID cohortId) {
        List<UUID> ids = jdbc.query(
                "SELECT course_id FROM cohort WHERE id = ?",
                (rs, row) -> rs.getObject("course_id", UUID.class),
                cohortId);
        if (ids.isEmpty()) {
            throw new Rejection(Failure.NOT_FOUND);
        }
        return ids.get(0);
    }

    private boolean practiceInCohort(UUID cohortId, UUID practiceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice WHERE id = ? AND cohort_id = ?",
                Integer.class,
                practiceId,
                cohortId);
        return count != null && count > 0;
    }

    private boolean examInCohort(UUID cohortId, UUID examId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE id = ? AND cohort_id = ?",
                Integer.class,
                examId,
                cohortId);
        return count != null && count > 0;
    }

    private boolean groupInCohort(UUID cohortId, UUID groupId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_group WHERE id = ? AND cohort_id = ?",
                Integer.class,
                groupId,
                cohortId);
        return count != null && count > 0;
    }

    private boolean enrolled(UUID cohortId, String student) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE cohort_id = ? AND student = ?",
                Integer.class,
                cohortId,
                student);
        return count != null && count > 0;
    }

}
