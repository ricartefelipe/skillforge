import Link from "next/link";
import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";
import { loadReports } from "../../lib/report";
import { Shell } from "../shell";

export default async function Page() {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Prazos</p>
            <h1>Agenda</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const reports = await loadReports(access);
  const items = deadlines(reports);
  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Prazos</p>
          <h1>Agenda</h1>
          <p className="lede">Práticas e provas que fecham numa data.</p>
        </header>
        {reports === null ? <p className="empty">A agenda não responde.</p> : null}
        {reports !== null && items.length === 0 ? <p className="empty">Nenhum prazo marcado.</p> : null}
        <ul>
          {items.map((item) => (
            <li key={item.id}>
              <Link href={`/turmas/${item.cohortId}`}>{item.title}</Link>
              <span className="kind">{item.kind}</span>
              <span className="meta">{item.when}</span>
              <span className="course-name">{item.cohortName}</span>
            </li>
          ))}
        </ul>
      </div>
    </Shell>
  );
}

function deadlines(reports: Awaited<ReturnType<typeof loadReports>>) {
  if (reports === null) {
    return [];
  }
  const items: { id: string; title: string; kind: string; when: string; cohortId: string; cohortName: string }[] = [];
  for (const report of reports) {
    for (const practice of report.practices) {
      if (practice.dueAt) {
        items.push({
          id: practice.id,
          title: practice.title,
          kind: "Prática",
          when: practice.dueAt,
          cohortId: report.cohortId,
          cohortName: report.cohortName,
        });
      }
    }
    for (const exam of report.exams) {
      if (exam.dueAt) {
        items.push({
          id: exam.id,
          title: exam.title,
          kind: "Prova",
          when: exam.dueAt,
          cohortId: report.cohortId,
          cohortName: report.cohortName,
        });
      }
    }
  }
  return items.sort((left, right) => left.when.localeCompare(right.when));
}
