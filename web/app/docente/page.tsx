import Link from "next/link";
import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { catalogNotice, coursesFromBody } from "../../lib/catalog";
import { cohortsFromBody } from "../../lib/cohort";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";
import { readIds, readJson } from "../../lib/api-read";
import { loadReports } from "../../lib/report";
import { createCourse, createLesson, createModule } from "../catalogo/actions";
import { CatalogView } from "../catalogo/catalog";
import { ExamForm } from "../exam-form";
import { announce, openExam } from "../turmas/actions";
import { Shell } from "../shell";

export default async function Page({ searchParams }: { searchParams: Promise<{ erro?: string }> }) {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Bancada</p>
            <h1>Docente</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  if (session.role === "aluno") {
    return (
      <Shell session={session}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Bancada</p>
            <h1>Docente</h1>
          </header>
          <p className="empty">Esta área é de quem ministra.</p>
        </div>
      </Shell>
    );
  }

  const params = await searchParams;
  const cohorts = await loadCohorts(access);
  const courses = await loadCourses(access);
  const teaching = session.role === "administracao" ? [] : await readIds(access, "/courses/teaching");
  const publishable =
    courses === null || session.role === "administracao"
      ? courses
      : courses.filter((course) => teaching.includes(course.id));
  const reports = await loadReports(access);
  const waiting = new Map<string, number>();
  for (const report of reports ?? []) {
    const open = report.practices.reduce(
      (count, practice) => count + practice.deliveries.filter((delivery) => delivery.feedback === null).length,
      0,
    );
    waiting.set(report.cohortId, open);
  }

  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Bancada</p>
          <h1>Docente</h1>
          <p className="lede">
            A grade é o curso, dividido em unidades. Em cada unidade você publica página, ebook e vídeo.
          </p>
        </header>
        <CatalogView
          courses={publishable}
          canCompose={session.role === "administracao"}
          writableCourseIds={teaching}
          notice={catalogNotice(params.erro ?? null)}
          createCourse={createCourse}
          createModule={createModule}
          createLesson={createLesson}
          heading="Publicar aula"
          kicker="Material"
          returnTo="/docente"
          titleAs="h2"
        />
        {cohorts === null ? <p className="empty">As turmas não respondem.</p> : null}
        {cohorts !== null && cohorts.length === 0 ? <p className="empty">Você ainda não ministra uma turma.</p> : null}
        {cohorts?.map((cohort) => (
          <section className="tree" key={cohort.id} aria-label={cohort.name}>
            <h2>
              <Link href={`/turmas/${cohort.id}`}>{cohort.name}</Link>
            </h2>
            <p className="course-name">{cohort.courseName}</p>
            <p className="tally">{waiting.get(cohort.id) ?? 0} entregas sem devolutiva</p>
            {(reports?.find((report) => report.cohortId === cohort.id)?.exams ?? []).map((exam) => (
              <p key={exam.id} className="tally">
                Prova {exam.title}: {exam.scores.length} nota{exam.scores.length === 1 ? "" : "s"}
              </p>
            ))}
            <ExamForm cohortId={cohort.id} action={openExam} returnTo="/docente" />
            <form className="compose" action={announce} aria-label={`Aviso de ${cohort.name}`}>
              <input type="hidden" name="cohortId" value={cohort.id} />
              <input type="hidden" name="returnTo" value="/docente" />
              <label>
                Título do aviso
                <input name="title" required />
              </label>
              <label>
                Aviso
                <textarea name="body" required />
              </label>
              <button type="submit">Publicar aviso</button>
            </form>
            <p>
              <Link href={`/turmas/${cohort.id}`}>Registrar chamada nesta turma</Link>
            </p>
          </section>
        ))}
      </div>
    </Shell>
  );
}

async function loadCourses(token: string) {
  const read = await readJson(token, "/courses");
  if (read === null) {
    return null;
  }
  return coursesFromBody(read.ok, read.body);
}

async function loadCohorts(token: string) {
  const read = await readJson(token, "/cohorts");
  if (read === null) {
    return null;
  }
  return cohortsFromBody(read.ok, read.body);
}
