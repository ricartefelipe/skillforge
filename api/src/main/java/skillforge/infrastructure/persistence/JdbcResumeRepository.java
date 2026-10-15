package skillforge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import skillforge.domain.identity.Actor;
import skillforge.domain.resume.ResumeRepository;
import skillforge.domain.studio.Resume;

@Component
class JdbcResumeRepository implements ResumeRepository {

    private final CohortGate gate;

    JdbcResumeRepository(CohortGate gate) {
        this.gate = gate;
    }

    @Override
    public Optional<Resume> resume(Actor actor) {
        gate.ensure();
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

    private List<Resume> nextForStudent(String student) {
        List<Resume> current = incomplete(student, true);
        if (!current.isEmpty()) {
            return current;
        }
        return incomplete(student, false);
    }

    private List<Resume> incomplete(String student, boolean sameCourse) {
        String courseFilter = sameCourse ? """
                AND course.id = (
                    SELECT progress_course.id
                    FROM completion
                    JOIN cohort progress_cohort ON progress_cohort.id = completion.cohort_id
                    JOIN course progress_course ON progress_course.id = progress_cohort.course_id
                    WHERE completion.student = ?
                    GROUP BY progress_course.id, progress_course.name
                    ORDER BY COUNT(*) DESC, progress_course.name
                    LIMIT 1
                )
                """ : "";
        String sql = """
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
                """ + courseFilter + """
                ORDER BY course.name, module.position, lesson.position
                LIMIT 1
                """;
        if (sameCourse) {
            return gate.jdbc.query(
                    sql,
                    (rs, row) -> new Resume(rs.getObject("id", UUID.class), rs.getString("title")),
                    student,
                    student,
                    student);
        }
        return gate.jdbc.query(
                sql,
                (rs, row) -> new Resume(rs.getObject("id", UUID.class), rs.getString("title")),
                student,
                student);
    }

    private List<Resume> nextForTeacher(String teacher) {
        return gate.jdbc.query("""
                SELECT lesson.id, lesson.title
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                JOIN cohort ON cohort.course_id = module.course_id AND cohort.teacher = ?
                ORDER BY module.position, lesson.position
                LIMIT 1
                """, (rs, row) -> new Resume(rs.getObject("id", UUID.class), rs.getString("title")), teacher);
    }

    private List<Resume> nextInCatalog() {
        return gate.jdbc.query("""
                SELECT lesson.id, lesson.title
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                ORDER BY module.position, lesson.position
                LIMIT 1
                """, (rs, row) -> new Resume(rs.getObject("id", UUID.class), rs.getString("title")));
    }

}
