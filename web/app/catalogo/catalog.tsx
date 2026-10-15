import Link from "next/link";

import type { Course, LessonSummary } from "../../lib/catalog";
import { LessonForm } from "./lesson-form";

function kindLabel(kind: LessonSummary["kind"]): string {
  switch (kind) {
    case "pdf":
      return "PDF";
    case "video":
      return "Vídeo";
    default:
      return "Markdown";
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
          {writes(course.id) ? (
            <form className="compose" action={createModule} aria-label={`Novo módulo em ${course.name}`}>
              <input type="hidden" name="courseId" value={course.id} />
              <input type="hidden" name="returnTo" value={returnTo} />
              <label>
                Nome do módulo
                <input name="name" required />
              </label>
              <button type="submit">Criar módulo</button>
            </form>
          ) : null}
          {course.modules.length === 0 ? <p className="empty">Nenhum módulo ainda.</p> : null}
          {course.modules.map((module) => (
            <section className="module" key={module.id} aria-label={module.name}>
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
                  <p className="meta">Publicar aula neste módulo</p>
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
