import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { canCompose, catalogNotice, coursesFromBody } from "../../lib/catalog";
import { ACCESS_COOKIE } from "../../lib/cookies";
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
          <h1>Catálogo</h1>
          <p>Entre com a sua conta.</p>
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
        notice={catalogNotice(params.erro ?? null)}
        createCourse={createCourse}
        createModule={createModule}
        createLesson={createLesson}
      />
    </Shell>
  );
}

async function loadCourses(token: string) {
  try {
    const response = await fetch(`${apiUrl()}/courses`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return coursesFromBody(response.ok, body);
  } catch {
    return null;
  }
}
