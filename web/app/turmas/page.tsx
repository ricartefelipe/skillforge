import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { coursesFromBody } from "../../lib/catalog";
import { canManageCohorts, cohortNotice, cohortsFromBody } from "../../lib/cohort";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";
import { Shell } from "../shell";
import { openCohort } from "./actions";
import { RosterView } from "./roster";

export default async function Page({ searchParams }: { searchParams: Promise<{ erro?: string }> }) {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Prática</p>
            <h1>Turmas</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const params = await searchParams;
  const courses = canManageCohorts(session.role) ? await loadCourses(access) : null;
  return (
    <Shell session={session}>
      <RosterView
        cohorts={await loadCohorts(access)}
        courses={courses}
        role={session.role}
        notice={cohortNotice(params.erro ?? null)}
        openCohort={openCohort}
      />
    </Shell>
  );
}

async function loadCohorts(token: string) {
  try {
    const response = await fetch(`${apiUrl()}/cohorts`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return cohortsFromBody(response.ok, body);
  } catch {
    return null;
  }
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
