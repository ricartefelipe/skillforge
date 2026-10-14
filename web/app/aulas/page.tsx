import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { coursesFromBody } from "../../lib/catalog";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { readIds, readJson } from "../../lib/api-read";
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
        writableCourseIds={session.role === "aluno" ? [] : await readIds(access, "/courses/teaching")}
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
  const read = await readJson(token, "/courses/curriculum");
  if (read === null) {
    return null;
  }
  return coursesFromBody(read.ok, read.body);
}
