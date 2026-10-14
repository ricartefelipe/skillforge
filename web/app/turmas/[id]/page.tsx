import { cookies } from "next/headers";

import { loadSession } from "../../../lib/access";
import { cohortDetailFromBody, cohortNotice, type ClassNotice, type RollCall } from "../../../lib/cohort";
import { ACCESS_COOKIE } from "../../../lib/cookies";
import { apiUrl } from "../../../lib/openid";
import { Shell } from "../../shell";
import {
  announce,
  callRoll,
  completeLesson,
  enrollStudent,
  openExam,
  openPractice,
  returnPractice,
  submitPractice,
  takeExam,
} from "../actions";
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
        announcements={await loadNotices(access, id)}
        rolls={await loadRolls(access, id)}
        announce={announce}
        callRoll={callRoll}
      />
    </Shell>
  );
}

async function loadNotices(token: string, id: string): Promise<ClassNotice[]> {
  const body = await loadJson(token, `/cohorts/${id}/announcements`);
  if (!Array.isArray(body)) {
    return [];
  }
  return body.flatMap((item) => {
    if (item === null || typeof item !== "object") {
      return [];
    }
    const record = item as Record<string, unknown>;
    if (typeof record.id !== "string" || typeof record.title !== "string" || typeof record.body !== "string") {
      return [];
    }
    return [{ id: record.id, title: record.title, body: record.body }];
  });
}

async function loadRolls(token: string, id: string): Promise<RollCall[]> {
  const body = await loadJson(token, `/cohorts/${id}/attendance`);
  if (!Array.isArray(body)) {
    return [];
  }
  return body.flatMap((item) => {
    if (item === null || typeof item !== "object") {
      return [];
    }
    const record = item as Record<string, unknown>;
    if (typeof record.heldOn !== "string" || !Array.isArray(record.marks)) {
      return [];
    }
    const marks = record.marks.flatMap((mark) => {
      if (mark === null || typeof mark !== "object") {
        return [];
      }
      const row = mark as Record<string, unknown>;
      if (typeof row.student !== "string" || typeof row.present !== "boolean") {
        return [];
      }
      return [{ student: row.student, present: row.present }];
    });
    return [{ heldOn: record.heldOn, marks }];
  });
}

async function loadJson(token: string, path: string): Promise<unknown> {
  try {
    const response = await fetch(`${apiUrl()}${path}`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!response.ok) {
      return null;
    }
    return await response.json();
  } catch {
    return null;
  }
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
