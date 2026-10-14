import { cookies } from "next/headers";

import { loadSession } from "../../../lib/access";
import { cohortDetailFromBody, cohortNotice } from "../../../lib/cohort";
import { ACCESS_COOKIE } from "../../../lib/cookies";
import { apiUrl } from "../../../lib/openid";
import { Shell } from "../../shell";
import { completeLesson, enrollStudent, openExam, openPractice, returnPractice, submitPractice, takeExam } from "../actions";
import { CohortDetailView } from "../detail";

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
            <p className="kicker">Turma</p>
            <h1>Turma</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const cohort = await loadCohort(access, id);
  if (!cohort) {
    return (
      <Shell session={session}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Turma</p>
            <h1>Turma</h1>
          </header>
          <p className="empty">Não encontrei essa turma.</p>
        </div>
      </Shell>
    );
  }

  const query = await searchParams;
  return (
    <Shell session={session}>
      <CohortDetailView
        cohort={cohort}
        role={session.role}
        notice={cohortNotice(query.erro ?? null)}
        enrollStudent={enrollStudent}
        completeLesson={completeLesson}
        openPractice={openPractice}
        submitPractice={submitPractice}
        returnPractice={returnPractice}
        openExam={openExam}
        takeExam={takeExam}
      />
    </Shell>
  );
}

async function loadCohort(token: string, id: string) {
  try {
    const response = await fetch(`${apiUrl()}/cohorts/${id}`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!response.ok) {
      return null;
    }
    const body: unknown = await response.json();
    return cohortDetailFromBody(true, body);
  } catch {
    return null;
  }
}
