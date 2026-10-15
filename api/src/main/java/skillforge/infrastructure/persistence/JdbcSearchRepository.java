package skillforge.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import skillforge.domain.identity.Actor;
import skillforge.domain.search.SearchRepository;
import skillforge.domain.studio.Hit;

@Component
class JdbcSearchRepository implements SearchRepository {

    private final CohortGate gate;

    JdbcSearchRepository(CohortGate gate) {
        this.gate = gate;
    }

    @Override
    public List<Hit> search(Actor actor, String query) {
        gate.ensure();
        String needle = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String role = actor.role().wire();
        String name = actor.username();
        List<Hit> hits = new ArrayList<>();
        hits.addAll(gate.jdbc.query("""
                SELECT lesson.title, '/catalogo/aulas/' || lesson.id::text
                FROM lesson
                JOIN module ON module.id = lesson.module_id
                JOIN course ON course.id = module.course_id
                WHERE (lesson.title ILIKE ? ESCAPE '\\' OR COALESCE(lesson.markdown, '') ILIKE ? ESCAPE '\\')
                """ + visibleCourse() + """
                ORDER BY lesson.title
                LIMIT 20
                """, (rs, row) -> new Hit("aula", rs.getString(1), rs.getString(2)), needle, needle, role, name, name));
        hits.addAll(gate.jdbc.query("""
                SELECT practice.title, '/turmas/' || cohort.id::text
                FROM practice
                JOIN cohort ON cohort.id = practice.cohort_id
                WHERE (practice.title ILIKE ? ESCAPE '\\' OR practice.prompt ILIKE ? ESCAPE '\\')
                """ + visibleCohort() + """
                ORDER BY practice.title
                LIMIT 20
                """, (rs, row) -> new Hit("pratica", rs.getString(1), rs.getString(2)),
                needle,
                needle,
                role,
                name,
                name));
        hits.addAll(gate.jdbc.query("""
                SELECT exam.title, '/turmas/' || cohort.id::text
                FROM exam
                JOIN cohort ON cohort.id = exam.cohort_id
                WHERE (
                    exam.title ILIKE ? ESCAPE '\\'
                    OR EXISTS (
                        SELECT 1 FROM question
                        WHERE question.exam_id = exam.id AND question.prompt ILIKE ? ESCAPE '\\'
                    )
                )
                """ + visibleCohort() + """
                ORDER BY exam.title
                LIMIT 20
                """, (rs, row) -> new Hit("prova", rs.getString(1), rs.getString(2)),
                needle,
                needle,
                role,
                name,
                name));
        return List.copyOf(hits);
    }

    private static String visibleCourse() {
        return """
                AND (
                    ? = 'administracao'
                    OR EXISTS (SELECT 1 FROM cohort WHERE cohort.course_id = course.id AND cohort.teacher = ?)
                    OR EXISTS (
                        SELECT 1 FROM cohort
                        JOIN enrollment ON enrollment.cohort_id = cohort.id
                        WHERE cohort.course_id = course.id AND enrollment.student = ?
                    )
                )
                """;
    }

    private static String visibleCohort() {
        return """
                AND (
                    ? = 'administracao'
                    OR cohort.teacher = ?
                    OR EXISTS (
                        SELECT 1 FROM enrollment
                        WHERE enrollment.cohort_id = cohort.id AND enrollment.student = ?
                    )
                )
                """;
    }

}
