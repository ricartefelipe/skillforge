import Link from "next/link";

import { canManageCohorts, type CohortDetail } from "../../lib/cohort";
import type { LessonSummary } from "../../lib/catalog";

function kindLabel(kind: LessonSummary["kind"]): string {
  switch (kind) {
    case "pdf":
      return "PDF";
    case "video":
      return "Vídeo";
    case "markdown":
      return "Markdown";
    default: {
      const uncovered: never = kind;
      return uncovered;
    }
  }
}

export function CohortDetailView({
  cohort,
  role,
  notice,
  enrollStudent,
}: {
  cohort: CohortDetail;
  role: string;
  notice: string | null;
  enrollStudent: (formData: FormData) => void;
}) {
  return (
    <main className="floor catalog">
      <p className="mark">Skillforge</p>
      <h1>{cohort.name}</h1>
      <Link className="back" href="/turmas">
        Voltar
      </Link>
      {notice ? <p role="alert">{notice}</p> : null}
      <section className="tree" aria-label={cohort.course.name}>
        <p className="meta">Curso</p>
        <h2>{cohort.course.name}</h2>
        <p className="meta">Docente</p>
        <p>{cohort.teacher}</p>
        {cohort.course.modules.length === 0 ? <p>Nenhuma aula ainda.</p> : null}
        {cohort.course.modules.map((module) => (
          <section key={module.id} aria-label={module.name}>
            <h3>{module.name}</h3>
            <ul>
              {module.lessons.map((lesson) => (
                <li key={lesson.id}>
                  <Link href={`/catalogo/aulas/${lesson.id}`}>{lesson.title}</Link>
                  <span className="kind">{kindLabel(lesson.kind)}</span>
                </li>
              ))}
            </ul>
          </section>
        ))}
        {cohort.students ? (
          <>
            <h3>Alunos</h3>
            {cohort.students.length === 0 ? (
              <p>Nenhum aluno matriculado.</p>
            ) : (
              <ul aria-label="Alunos">
                {cohort.students.map((student) => (
                  <li key={student}>{student}</li>
                ))}
              </ul>
            )}
          </>
        ) : null}
        {canManageCohorts(role) ? (
          <form action={enrollStudent} aria-label="Matricular aluno">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Conta do aluno
              <input name="student" required autoComplete="off" />
            </label>
            <button type="submit">Matricular</button>
          </form>
        ) : null}
      </section>
    </main>
  );
}
