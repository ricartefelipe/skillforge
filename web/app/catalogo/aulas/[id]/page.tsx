import { cookies } from "next/headers";

import { loadSession } from "../../../../lib/access";
import { catalogNotice, coursesFromBody, kindLabel, lessonFromBody, nextInTrail, type Course } from "../../../../lib/catalog";
import { readJson } from "../../../../lib/api-read";
import { ACCESS_COOKIE } from "../../../../lib/cookies";
import { doubtsFromBody } from "../../../../lib/forum";
import { apiUrl } from "../../../../lib/openid";
import Link from "next/link";

import { Shell } from "../../../shell";
import { LessonView } from "../../lesson";
import { addAsset, reviseLesson } from "../../actions";
import { openDoubt, replyDoubt } from "../actions";
import { Forum } from "../../forum";

export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ erro?: string }>;
}) {
  const { id } = await params;
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Aula</p>
            <h1>Aula</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const lesson = await loadLesson(access, id);
  if (!lesson) {
    return (
      <Shell session={session}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Aula</p>
            <h1>Aula</h1>
          </header>
          <p className="empty">Não encontrei essa aula.</p>
        </div>
      </Shell>
    );
  }

  const query = await searchParams;
  const notice = catalogNotice(query.erro ?? null);
  const found = courseOf(await loadCurriculum(access), id);
  const finished = await finishedLessons(access, found?.id ?? null);
  const course = found ? withCompletion(found, finished) : null;
  const next = course ? nextInTrail(course, id) : null;
  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Curso</p>
          {course ? <p className="course-name">{course.name}</p> : null}
          <h1>{lesson.title}</h1>
        </header>
        {notice ? <p role="alert">{notice}</p> : null}
        <div className="player">
          <aside className="playlist" aria-label="Aulas do curso">
            {course?.modules.map((module) => (
              <section key={module.id} aria-label={module.name}>
                <p className="meta">{module.name}</p>
                {module.lessons.map((item) => (
                  <Link key={item.id} href={`/catalogo/aulas/${item.id}`} aria-current={item.id === id ? "page" : undefined}>
                    <span>{item.title}</span>
                    <span className="kind">
                      {kindLabel(item.kind)}
                      {item.completed ? " · Concluída" : ""}
                    </span>
                  </Link>
                ))}
              </section>
            ))}
          </aside>
          <div>
            <LessonView lesson={lesson} />
            {next ? (
              <p className="next-lesson">
                <Link href={`/catalogo/aulas/${next.id}`}>Próxima aula: {next.title}</Link>
              </p>
            ) : null}
            <AssetList lessonId={id} assets={await loadAssets(access, id)} />
            {session.role === "docente" || session.role === "administracao" ? (
              <>
                <form className="compose" action={reviseLesson} aria-label="Editar aula">
                  <input type="hidden" name="lessonId" value={id} />
                  <label>
                    Título
                    <input name="title" defaultValue={lesson.title} required />
                  </label>
                  {lesson.kind === "markdown" ? (
                    <label>
                      Texto
                      <textarea name="markdown" defaultValue={lesson.markdown ?? ""} required />
                    </label>
                  ) : null}
                  <button type="submit">Guardar aula</button>
                </form>
                <form className="compose" action={addAsset} aria-label="Arquivo da aula">
                  <input type="hidden" name="lessonId" value={id} />
                  <label>
                    Nome do arquivo
                    <input name="name" required />
                  </label>
                  <label>
                    Tipo
                    <select name="kind" defaultValue="ebook">
                      <option value="ebook">Ebook</option>
                      <option value="video">Vídeo</option>
                    </select>
                  </label>
                  <label>
                    Arquivo
                    <input name="file" type="file" required />
                  </label>
                  <button type="submit">Anexar arquivo</button>
                </form>
              </>
            ) : null}
            <Forum lessonId={id} doubts={await loadDoubts(access, id)} openDoubt={openDoubt} replyDoubt={replyDoubt} />
          </div>
        </div>
      </div>
    </Shell>
  );
}

function AssetList({ lessonId, assets }: { lessonId: string; assets: { id: string; name: string }[] }) {
  if (assets.length === 0) {
    return null;
  }
  return (
    <section aria-label="Arquivos da aula">
      <h2>Arquivos</h2>
      <ul>
        {assets.map((asset) => (
          <li key={asset.id}>
            <a href={`/catalogo/aulas/${lessonId}/arquivos/${asset.id}`}>{asset.name}</a>
          </li>
        ))}
      </ul>
    </section>
  );
}

async function loadAssets(token: string, id: string) {
  try {
    const response = await fetch(`${apiUrl()}/lessons/${id}/assets`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!response.ok) {
      return [];
    }
    const body: unknown = await response.json();
    if (!Array.isArray(body)) {
      return [];
    }
    return body.flatMap((item) => {
      if (item === null || typeof item !== "object") {
        return [];
      }
      const record = item as Record<string, unknown>;
      if (typeof record.id !== "string" || typeof record.name !== "string") {
        return [];
      }
      return [{ id: record.id, name: record.name }];
    });
  } catch {
    return [];
  }
}

function withCompletion(course: Course, finished: Set<string>): Course {
  return {
    ...course,
    modules: course.modules.map((module) => ({
      ...module,
      lessons: module.lessons.map((lesson) => ({ ...lesson, completed: finished.has(lesson.id) })),
    })),
  };
}

async function finishedLessons(token: string, courseId: string | null): Promise<Set<string>> {
  if (courseId === null) {
    return new Set();
  }
  const list = await readJson(token, "/cohorts");
  if (list === null || !list.ok || !Array.isArray(list.body)) {
    return new Set();
  }
  const cohortId = list.body.flatMap((item) => {
    if (item === null || typeof item !== "object") {
      return [];
    }
    const record = item as Record<string, unknown>;
    return record.courseId === courseId && typeof record.id === "string" ? [record.id] : [];
  })[0];
  if (cohortId === undefined) {
    return new Set();
  }
  const detail = await readJson(token, `/cohorts/${cohortId}`);
  if (detail === null || !detail.ok || detail.body === null || typeof detail.body !== "object") {
    return new Set();
  }
  const course = (detail.body as Record<string, unknown>).course;
  const ids = new Set<string>();
  if (course === null || typeof course !== "object" || !Array.isArray((course as { modules?: unknown }).modules)) {
    return ids;
  }
  for (const unit of (course as { modules: unknown[] }).modules) {
    if (unit === null || typeof unit !== "object" || !Array.isArray((unit as { lessons?: unknown }).lessons)) {
      continue;
    }
    for (const lesson of (unit as { lessons: unknown[] }).lessons) {
      if (lesson === null || typeof lesson !== "object") {
        continue;
      }
      const record = lesson as Record<string, unknown>;
      if (record.completed === true && typeof record.id === "string") {
        ids.add(record.id);
      }
    }
  }
  return ids;
}

function courseOf(courses: Course[] | null, lessonId: string) {
  return courses?.find((course) => course.modules.some((module) => module.lessons.some((lesson) => lesson.id === lessonId))) ?? null;
}

async function loadCurriculum(token: string) {
  const read = await readJson(token, "/courses/curriculum");
  if (read === null) {
    return null;
  }
  return coursesFromBody(read.ok, read.body);
}

async function loadDoubts(token: string, id: string) {
  try {
    const response = await fetch(`${apiUrl()}/lessons/${id}/doubts`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return doubtsFromBody(response.ok, body);
  } catch {
    return null;
  }
}

async function loadLesson(token: string, id: string) {
  try {
    const response = await fetch(`${apiUrl()}/lessons/${id}`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return lessonFromBody(response.ok, body);
  } catch {
    return null;
  }
}
