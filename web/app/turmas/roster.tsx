import Link from "next/link";

import { canManageCohorts, emptyCohorts, progressLabel, type CohortSummary } from "../../lib/cohort";
import type { Course } from "../../lib/catalog";
import { ProgressMeter } from "../meter";

export function RosterView({
  cohorts,
  courses,
  role,
  notice,
  openCohort,
}: {
  cohorts: CohortSummary[] | null;
  courses: Course[] | null;
  role: string;
  notice: string | null;
  openCohort: (formData: FormData) => void;
}) {
  const manage = canManageCohorts(role);
  return (
    <div className="page">
      <header className="intro">
        <p className="kicker">Prática</p>
        <h1>Turmas</h1>
      </header>
      {notice ? <p role="alert">{notice}</p> : null}
      {manage && courses !== null && courses.length > 0 ? (
        <form className="sheet" action={openCohort} aria-label="Nova turma">
          <label>
            Nome da turma
            <input name="name" required />
          </label>
          <label>
            Curso
            <select name="courseId" required defaultValue={courses[0]?.id}>
              {courses.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Conta do docente
            <input name="teacher" required autoComplete="off" />
          </label>
          <button type="submit">Abrir turma</button>
        </form>
      ) : null}
      {manage && courses !== null && courses.length === 0 ? (
        <p className="empty">Abra um curso no catálogo antes da turma.</p>
      ) : null}
      {cohorts === null ? <p className="empty">As turmas não respondem.</p> : null}
      {cohorts !== null && cohorts.length === 0 ? <p className="empty">{emptyCohorts(role)}</p> : null}
      {cohorts?.map((cohort) => (
        <section className="tree" key={cohort.id} aria-label={cohort.name}>
          <h2>
            <Link href={`/turmas/${cohort.id}`}>{cohort.name}</Link>
          </h2>
          <p className="course-name">{cohort.courseName}</p>
          <p className="meta">Ministrada por {cohort.teacher}</p>
          {cohort.completed !== null && cohort.total !== null ? (
            <>
              <p className="tally">{progressLabel(cohort.completed, cohort.total)}</p>
              <ProgressMeter completed={cohort.completed} total={cohort.total} />
            </>
          ) : null}
          {cohort.students ? (
            <>
              <h3>Alunos</h3>
              {cohort.students.length === 0 ? (
                <p className="empty">Nenhum aluno matriculado.</p>
              ) : (
                <ul aria-label={`Alunos de ${cohort.name}`}>
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
        </section>
      ))}
    </div>
  );
}
