import { cookies } from "next/headers";

import { loadSession } from "../../../lib/access";
import {
  cohortDetailFromBody,
  cohortNotice,
  type BankItem,
  type ClassNotice,
  type PeerWork,
  type RollCall,
  type StudyGroup,
} from "../../../lib/cohort";
import { ACCESS_COOKIE } from "../../../lib/cookies";
import { apiUrl } from "../../../lib/openid";
import { Shell } from "../../shell";
import {
  announce,
  callRoll,
  completeLesson,
  enrollStudent,
  openExam,
  openGroup,
  openPractice,
  removeExam,
  removePractice,
  returnPractice,
  reviewPeer,
  reviseAnnouncement,
  revisePractice,
  reorderGrade,
  reviseQuestions,
  saveQuestion,
  submitPractice,
  takeExam,
  weighCohort,
  withdrawNotice,
} from "../actions";
import { createLesson, createModule, removeLesson } from "../../catalogo/actions";
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
        createModule={createModule}
        createLesson={createLesson}
        removeLesson={removeLesson}
        removePractice={removePractice}
        removeExam={removeExam}
        withdrawNotice={withdrawNotice}
        bank={await loadBank(access, cohort.course.id)}
        groups={await loadGroups(access, id)}
        peers={await loadPeers(access, id, cohort.practices.map((practice) => practice.id))}
        revisePractice={revisePractice}
        reviseAnnouncement={reviseAnnouncement}
        weighCohort={weighCohort}
        openGroup={openGroup}
        reviewPeer={reviewPeer}
        saveQuestion={saveQuestion}
        reorderGrade={reorderGrade}
        reviseQuestions={reviseQuestions}
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

async function loadBank(token: string, courseId: string): Promise<BankItem[]> {
  const body = await loadJson(token, `/courses/${courseId}/bank`);
  if (!Array.isArray(body)) {
    return [];
  }
  return body.flatMap((item) => {
    if (item === null || typeof item !== "object") {
      return [];
    }
    const record = item as Record<string, unknown>;
    if (typeof record.id !== "string" || typeof record.prompt !== "string") {
      return [];
    }
    return [{ id: record.id, prompt: record.prompt }];
  });
}

async function loadGroups(token: string, id: string): Promise<StudyGroup[]> {
  const body = await loadJson(token, `/cohorts/${id}/groups`);
  if (!Array.isArray(body)) {
    return [];
  }
  return body.flatMap((item) => {
    if (item === null || typeof item !== "object") {
      return [];
    }
    const record = item as Record<string, unknown>;
    if (typeof record.id !== "string" || typeof record.name !== "string" || !Array.isArray(record.members)) {
      return [];
    }
    const members = record.members.filter((member): member is string => typeof member === "string");
    return [{ id: record.id, name: record.name, members }];
  });
}

async function loadPeers(token: string, cohortId: string, practiceIds: string[]): Promise<PeerWork[]> {
  const works: PeerWork[] = [];
  for (const practiceId of practiceIds) {
    const body = await loadJson(token, `/cohorts/${cohortId}/practices/${practiceId}/reviews`);
    if (!Array.isArray(body)) {
      continue;
    }
    for (const item of body) {
      if (item === null || typeof item !== "object") {
        continue;
      }
      const record = item as Record<string, unknown>;
      if (typeof record.author !== "string" || typeof record.text !== "string" || !Array.isArray(record.reviews)) {
        continue;
      }
      const reviews = record.reviews.flatMap((review) => {
        if (review === null || typeof review !== "object") {
          return [];
        }
        const row = review as Record<string, unknown>;
        if (typeof row.reviewer !== "string" || typeof row.comment !== "string" || typeof row.mark !== "number") {
          return [];
        }
        return [{ reviewer: row.reviewer, comment: row.comment, mark: row.mark }];
      });
      works.push({ practiceId, author: record.author, text: record.text, reviews });
    }
  }
  return works;
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
