import Link from "next/link";

import { canLeadPractices, canManageCohorts, progressLabel, type CohortDetail, type Practice } from "../../lib/cohort";
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
  openPractice,
  submitPractice,
  returnPractice,
}: {
  cohort: CohortDetail;
  role: string;
  notice: string | null;
  enrollStudent: (formData: FormData) => void;
  completeLesson: (formData: FormData) => void;
  openPractice: (formData: FormData) => void;
  submitPractice: (formData: FormData) => void;
  returnPractice: (formData: FormData) => void;
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
        <h3>Práticas</h3>
        {cohort.practices.length === 0 ? <p className="empty">Nenhuma prática ainda.</p> : null}
        {cohort.practices.map((practice) => (
          <PracticeCard
            key={practice.id}
            cohortId={cohort.id}
            practice={practice}
            role={role}
            submitPractice={submitPractice}
            returnPractice={returnPractice}
          />
        ))}
        {canLeadPractices(role) ? (
          <form className="compose" action={openPractice} aria-label="Nova prática">
            <input type="hidden" name="cohortId" value={cohort.id} />
            <label>
              Título
              <input name="title" required />
            </label>
            <label>
              Enunciado
              <textarea name="prompt" required />
            </label>
            <button type="submit">Abrir prática</button>
          </form>
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

function PracticeCard({
  cohortId,
  practice,
  role,
  submitPractice,
  returnPractice,
}: {
  cohortId: string;
  practice: Practice;
  role: string;
  submitPractice: (formData: FormData) => void;
  returnPractice: (formData: FormData) => void;
}) {
  const own = role === "aluno" ? (practice.deliveries[0] ?? null) : null;
  const waiting = own !== null && own.feedback === null;
  return (
    <section className="module" aria-label={practice.title}>
      <h3>{practice.title}</h3>
      <p>{practice.prompt}</p>
      {role === "aluno" && own === null ? (
        <form className="compose" action={submitPractice} aria-label="Entrega">
          <input type="hidden" name="cohortId" value={cohortId} />
          <input type="hidden" name="practiceId" value={practice.id} />
          <label>
            Texto
            <textarea name="text" required />
          </label>
          <button type="submit">Entregar</button>
        </form>
      ) : null}
      {role === "aluno" && own !== null ? (
        <>
          <p>{own.text}</p>
          {own.feedback === null ? <p className="meta">Aguardando devolutiva.</p> : <p>{own.feedback}</p>}
          {waiting ? (
            <form className="compose" action={submitPractice} aria-label="Entrega">
              <input type="hidden" name="cohortId" value={cohortId} />
              <input type="hidden" name="practiceId" value={practice.id} />
              <label>
                Texto
                <textarea name="text" required defaultValue={own.text} />
              </label>
              <button className="quiet" type="submit">Entregar de novo</button>
            </form>
          ) : null}
        </>
      ) : null}
      {canLeadPractices(role)
        ? practice.deliveries.map((delivery) => (
            <section key={delivery.student} aria-label={delivery.student}>
              <p className="meta">{delivery.student}</p>
              <p>{delivery.text}</p>
              {delivery.feedback === null ? (
                <form className="compose" action={returnPractice} aria-label={`Devolutiva para ${delivery.student}`}>
                  <input type="hidden" name="cohortId" value={cohortId} />
                  <input type="hidden" name="practiceId" value={practice.id} />
                  <input type="hidden" name="student" value={delivery.student} />
                  <label>
                    Devolutiva
                    <textarea name="text" required />
                  </label>
                  <button type="submit">Devolver</button>
                </form>
              ) : (
                <p>{delivery.feedback}</p>
              )}
            </section>
          ))
        : null}
    </section>
  );
}
