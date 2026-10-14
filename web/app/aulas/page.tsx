import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { coursesFromBody } from "../../lib/catalog";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";
import { Shell } from "../shell";
import { createCourse, createLesson, createModule } from "../catalogo/actions";
import { CatalogView } from "../catalogo/catalog";

export default async function Page() {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Grade</p>
            <h1>Aulas</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  return (
    <Shell session={session}>
      <CatalogView
        courses={await loadCurriculum(access)}
        canCompose={false}
        writableCourseIds={session.role === "aluno" ? [] : await loadTeaching(access)}
        notice={null}
        createCourse={createCourse}
        createModule={createModule}
        createLesson={createLesson}
        heading="Aulas"
        kicker="Grade cursada"
      />
    </Shell>
  );
}

async function loadCurriculum(token: string) {
  try {
    const response = await fetch(`${apiUrl()}/courses/curriculum`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return coursesFromBody(response.ok, body);
  } catch {
    return null;
  }
}

async function loadTeaching(token: string): Promise<string[]> {
  try {
    const response = await fetch(`${apiUrl()}/courses/teaching`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    if (!response.ok || !Array.isArray(body)) {
      return [];
    }
    return body.filter((item): item is string => typeof item === "string");
  } catch {
    return [];
  }
}
