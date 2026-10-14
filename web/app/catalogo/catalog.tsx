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
  notice,
  createCourse,
  createModule,
  createLesson,
}: {
  courses: Course[] | null;
  canCompose: boolean;
  notice: string | null;
  createCourse: (formData: FormData) => void;
  createModule: (formData: FormData) => void;
  createLesson: (formData: FormData) => void;
}) {
  return (
    <div className="page">
      <h1>Catálogo</h1>
      {notice ? <p role="alert">{notice}</p> : null}
      {canCompose ? (
        <form className="sheet" action={createCourse} aria-label="Novo curso">
          <label>
            Nome do curso
            <input name="name" required />
          </label>
          <button type="submit">Criar curso</button>
        </form>
      ) : null}
      {courses === null ? <p>O catálogo não responde.</p> : null}
      {courses !== null && courses.length === 0 ? <p>Nenhum curso ainda.</p> : null}
      {courses?.map((course) => (
        <section className="tree" key={course.id} aria-label={course.name}>
          <h2>{course.name}</h2>
          {canCompose ? (
            <form action={createModule} aria-label={`Novo módulo em ${course.name}`}>
              <input type="hidden" name="courseId" value={course.id} />
              <label>
                Nome do módulo
                <input name="name" required />
              </label>
              <button type="submit">Criar módulo</button>
            </form>
          ) : null}
          {course.modules.length === 0 ? <p>Nenhum módulo ainda.</p> : null}
          {course.modules.map((module) => (
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
              {canCompose ? <LessonForm moduleId={module.id} action={createLesson} /> : null}
            </section>
          ))}
        </section>
      ))}
    </div>
  );
}
