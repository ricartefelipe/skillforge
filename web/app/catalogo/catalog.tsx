import Link from "next/link";

import { kindLabel, type Course } from "../../lib/catalog";
import { LessonForm } from "./lesson-form";

export function CatalogView({
  courses,
  canCompose,
  writableCourseIds,
  notice,
  createCourse,
  createModule,
  createLesson,
  heading = "Cursos",
  kicker = "Material",
  lede = "O curso é o material. A entrega, a prova e a chamada ficam na turma.",
  emptyMessage = "Nenhum curso ainda.",
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
  lede?: string;
  emptyMessage?: string;
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
        <p className="lede">{lede}</p>
      </header>
      {notice ? <p role="alert">{notice}</p> : null}
      <form className="compose" action="/busca" method="get" aria-label="Buscar">
        <label>
          Achar uma aula
          <input name="q" defaultValue={query} placeholder="Nome da aula" />
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
      {courses !== null && courses.length === 0 ? <p className="empty">{emptyMessage}</p> : null}
      {courses?.map((course) => (
        <section className="tree" key={course.id} aria-label={course.name}>
          <h2>{course.name}</h2>
          <p className="course-name">
            {course.modules.length === 1 ? "1 unidade" : `${course.modules.length} unidades`}
          </p>
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
