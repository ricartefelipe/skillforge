import Link from "next/link";

import { canManageCohorts, progressLabel, type CohortDetail } from "../../lib/cohort";
import type { LessonSummary } from "../../lib/catalog";
import { ProgressMeter } from "../meter";

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
  completeLesson,
}: {
  cohort: CohortDetail;
  role: string;
  notice: string | null;
  enrollStudent: (formData: FormData) => void;
  completeLesson: (formData: FormData) => void;
}) {
  return (
    <div className="page">
      <header className="intro">
        <p className="kicker">Turma</p>
        <h1>{cohort.name}</h1>
      </header>
      {notice ? <p role="alert">{notice}</p> : null}
      <section className="tree" aria-label={cohort.course.name}>
        <p className="meta">Curso</p>
        <h2>{cohort.course.name}</h2>
        <p className="meta">Ministrada por {cohort.teacher}</p>
        {cohort.completed !== null && cohort.total !== null ? (
          <>
            <p className="tally">{progressLabel(cohort.completed, cohort.total)}</p>
            <ProgressMeter completed={cohort.completed} total={cohort.total} />
          </>
        ) : null}
        {cohort.course.modules.length === 0 ? <p className="empty">Nenhuma aula ainda.</p> : null}
        {cohort.course.modules.map((module) => (
          <section className="module" key={module.id} aria-label={module.name}>
            <h3>{module.name}</h3>
            <ul>
              {module.lessons.map((lesson) => (
                <li className="lesson-row" key={lesson.id}>
                  <Link href={`/catalogo/aulas/${lesson.id}`}>{lesson.title}</Link>
                  <span className="kind">{kindLabel(lesson.kind)}</span>
                  {lesson.completed === true ? <span className="done">Concluída</span> : null}
                  {role === "aluno" && lesson.completed === false ? (
                    <form action={completeLesson}>
                      <input type="hidden" name="cohortId" value={cohort.id} />
                      <input type="hidden" name="lessonId" value={lesson.id} />
                      <button className="quiet" type="submit">Concluir</button>
                    </form>
                  ) : null}
                </li>
              ))}
            </ul>
          </section>
        ))}
        {cohort.students ? (
          <>
            <h3>Alunos</h3>
            {cohort.students.length === 0 ? (
              <p className="empty">Nenhum aluno matriculado.</p>
            ) : (
              <ul aria-label="Alunos">
                {cohort.students.map((student) => (
                  <li className="person" key={student.student}>
                    <span>{student.student}</span>
                    <span className="tally">{progressLabel(student.completed, student.total)}</span>
                    <ProgressMeter completed={student.completed} total={student.total} />
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : null}
        {canManageCohorts(role) ? (
          <form className="compose" action={enrollStudent} aria-label="Matricular aluno">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Conta do aluno
              <input name="student" required autoComplete="off" />
            </label>
            <button type="submit">Matricular</button>
          </form>
        ) : null}
      </section>
    </div>
  );
}
