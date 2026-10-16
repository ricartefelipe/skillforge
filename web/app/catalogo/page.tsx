import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { canCompose, catalogNotice, courseOfLesson, coursesFromBody, narrowCourses, type Course } from "../../lib/catalog";
import { cohortsFromBody, progressLabel, type CohortSummary } from "../../lib/cohort";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { readIds, readJson } from "../../lib/api-read";
import { apiUrl } from "../../lib/openid";
import { Shell } from "../shell";
import { createCourse, createLesson, createModule } from "./actions";
import { CatalogView, type Study } from "./catalog";

export default async function Page({ searchParams }: { searchParams: Promise<{ erro?: string; q?: string }> }) {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Disponíveis</p>
            <h1>Cursos</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const params = await searchParams;
  const token = access ?? "";
  const studying = session.role !== "administracao";
  const courses = narrowCourses(await loadCourses(token, studying), params.q ?? "");
  const studies = session.role === "aluno" ? await studiesOf(token, courses) : undefined;
  return (
    <Shell session={session}>
      <CatalogView
        courses={courses}
        query={params.q ?? ""}
        canCompose={canCompose(session.role)}
        writableCourseIds={canCompose(session.role) ? [] : await readIds(token, "/courses/teaching")}
        notice={catalogNotice(params.erro ?? null)}
        createCourse={createCourse}
        createModule={createModule}
        createLesson={createLesson}
        heading={session.role === "aluno" ? "Seus cursos" : "Cursos"}
        lede={session.role === "aluno" && studies ? studentLede(studies) : courseLede(session.role)}
        emptyMessage={session.role === "aluno" ? "Você ainda não está em um curso." : "Nenhum curso ainda."}
        studies={studies}
      />
    </Shell>
  );
}

async function studiesOf(token: string, courses: Course[] | null): Promise<Study[] | undefined> {
  if (courses === null) {
    return undefined;
  }
  const cohorts = await loadCohorts(token);
  if (cohorts === null) {
    return undefined;
  }
  const resume = await loadResume(token);
  const current = resume ? courseOfLesson(courses, resume.lessonId) : null;
  return cohorts.map((cohort) => studyOf(cohort, current?.id ?? null, resume));
}

function studyOf(cohort: CohortSummary, currentCourseId: string | null, resume: Resume | null): Study {
  const current = cohort.courseId === currentCourseId;
  return {
    courseName: cohort.courseName,
    cohortName: cohort.name,
    cohortHref: `/turmas/${cohort.id}`,
    progress:
      cohort.completed !== null && cohort.total !== null ? progressLabel(cohort.completed, cohort.total) : null,
    current,
    examReady: examReady(cohort.completed, cohort.total),
    continueHref: current && resume ? `/catalogo/aulas/${resume.lessonId}` : null,
    continueTitle: current && resume ? resume.title : null,
  };
}

function examReady(completed: number | null, total: number | null): boolean {
  if (total == null || total === 0) {
    return true;
  }
  return (completed ?? 0) >= total;
}

function studentLede(studies: Study[]): string {
  const current = studies.find((study) => study.current);
  if (studies.length === 0) {
    return "Você ainda não está em um curso.";
  }
  if (studies.length === 1) {
    return `Você está cursando ${studies[0]?.courseName}.`;
  }
  if (current) {
    return `Você está em ${studies.length} cursos. O em andamento é ${current.courseName}.`;
  }
  return `Você está em ${studies.length} cursos.`;
}

type Resume = { lessonId: string; title: string };

async function loadResume(token: string): Promise<Resume | null> {
  const read = await readJson(token, "/resume");
  if (read === null || !read.ok || read.body === null || typeof read.body !== "object") {
    return null;
  }
  const record = read.body as Record<string, unknown>;
  if (typeof record.lessonId !== "string" || typeof record.title !== "string") {
    return null;
  }
  return { lessonId: record.lessonId, title: record.title };
}

async function loadCohorts(token: string) {
  const read = await readJson(token, "/cohorts");
  if (read === null) {
    return null;
  }
  return cohortsFromBody(read.ok, read.body);
}

function courseLede(role: string): string {
  switch (role) {
    case "aluno":
      return "Aqui está o material das suas turmas. Para entregar, fazer prova ou ver a chamada, abra a turma.";
    case "docente":
      return "Aqui você publica as aulas. A entrega e a nota acontecem na turma.";
    case "administracao":
      return "Aqui você cria o curso e publica as aulas. A matrícula e o docente ficam em Turmas.";
    default:
      return "O curso é o material. A entrega, a prova e a chamada ficam na turma.";
  }
}

async function loadCourses(token: string, studying: boolean) {
  const read = await readJson(token, studying ? "/courses/curriculum" : "/courses");
  if (read === null) {
    return null;
  }
  return coursesFromBody(read.ok, read.body);
}
