import Link from "next/link";

import type { Course, LessonSummary } from "../../lib/catalog";
import { LessonForm } from "./lesson-form";

function kindLabel(kind: LessonSummary["kind"]): string {
  switch (kind) {
    case "pdf":
    case "ebook":
      return "Ebook";
    case "video":
      return "Vídeo";
    case "markdown":
      return "Página";
  }
}

export function CatalogView({
  courses,
  canCompose,
  writableCourseIds,
  notice,
  createCourse,
  createModule,
  createLesson,
  heading = "Cursos",
  kicker = "Disponíveis",
  returnTo = "/catalogo",
  titleAs = "h1",
  query = "",
}: {
  courses: Course[] | null;
  canCompose: boolean;
  writableCourseIds: string[];
  notice: string | null;
  createCourse: (formData: FormData) => void;
  createModule: (formData: FormData) => void;
  createLesson: (formData: FormData) => void;
  heading?: string;
  kicker?: string;
  returnTo?: string;
  titleAs?: "h1" | "h2";
  query?: string;
}) {
  const Title = titleAs;
  const writes = (courseId: string) => canCompose || writableCourseIds.includes(courseId);
  return (
    <div className="page">
      <header className="intro">
        <p className="kicker">{kicker}</p>
        <Title>{heading}</Title>
      </header>
      {notice ? <p role="alert">{notice}</p> : null}
      <form className="compose" action={returnTo} method="get" aria-label="Buscar">
        <label>
          Buscar
          <input name="q" defaultValue={query} placeholder="Curso, unidade ou aula" />
        </label>
        <button type="submit">Buscar</button>
      </form>
      {canCompose ? (
        <form className="sheet" action={createCourse} aria-label="Novo curso">
          <input type="hidden" name="returnTo" value={returnTo} />
          <label>
            Nome do curso
            <input name="name" required />
          </label>
          <button type="submit">Criar curso</button>
        </form>
      ) : null}
      {courses === null ? <p className="empty">O catálogo não responde.</p> : null}
      {courses !== null && courses.length === 0 ? <p className="empty">Nenhum curso ainda.</p> : null}
      {courses?.map((course) => (
        <section className="tree" key={course.id} aria-label={course.name}>
          <h2>{course.name}</h2>
          <p className="course-name">Grade em unidades. Cada unidade reúne página, ebook e vídeo.</p>
          {writes(course.id) ? (
            <form className="compose" action={createModule} aria-label={`Nova unidade em ${course.name}`}>
              <input type="hidden" name="courseId" value={course.id} />
              <input type="hidden" name="returnTo" value={returnTo} />
              <label>
                Nome da unidade
                <input name="name" required />
              </label>
              <button type="submit">Abrir unidade</button>
            </form>
          ) : null}
          {course.modules.length === 0 ? <p className="empty">Nenhuma unidade ainda.</p> : null}
          {course.modules.map((module, index) => (
            <section className="module unit" key={module.id} aria-label={module.name}>
              <p className="meta">Unidade {index + 1}</p>
              <h3>{module.name}</h3>
              <ul>
                {module.lessons.map((lesson) => (
                  <li className="lesson-row" key={lesson.id}>
                    <Link href={`/catalogo/aulas/${lesson.id}`}>{lesson.title}</Link>
                    <span className="kind">{kindLabel(lesson.kind)}</span>
                  </li>
                ))}
              </ul>
              {writes(course.id) ? (
                <>
                  <p className="meta">Publicar material nesta unidade</p>
                  <LessonForm moduleId={module.id} action={createLesson} returnTo={returnTo} />
                </>
              ) : null}
            </section>
          ))}
        </section>
      ))}
    </div>
  );
}
