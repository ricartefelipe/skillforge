import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { canCompose, catalogNotice, coursesFromBody } from "../../lib/catalog";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { readIds, readJson } from "../../lib/api-read";
import { apiUrl } from "../../lib/openid";
import { Shell } from "../shell";
import { createCourse, createLesson, createModule } from "./actions";
import { CatalogView } from "./catalog";

export default async function Page({ searchParams }: { searchParams: Promise<{ erro?: string }> }) {
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
  return (
    <Shell session={session}>
      <CatalogView
        courses={await loadCourses(access ?? "")}
        canCompose={canCompose(session.role)}
        writableCourseIds={canCompose(session.role) ? [] : await readIds(access ?? "", "/courses/teaching")}
        notice={catalogNotice(params.erro ?? null)}
        createCourse={createCourse}
        createModule={createModule}
        createLesson={createLesson}
      />
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
