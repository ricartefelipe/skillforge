import Link from "next/link";
import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { cohortsFromBody } from "../../lib/cohort";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";
import { readJson } from "../../lib/api-read";
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
            <p className="kicker">Bancada</p>
            <h1>Docente</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  if (session.role === "aluno") {
    return (
      <Shell session={session}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Bancada</p>
            <h1>Docente</h1>
          </header>
          <p className="empty">Esta área é de quem ministra.</p>
        </div>
      </Shell>
    );
  }

  const cohorts = await loadCohorts(access);
  const reports = await loadReports(access);
  const waiting = new Map<string, number>();
  for (const report of reports ?? []) {
    const open = report.practices.reduce(
      (count, practice) => count + practice.deliveries.filter((delivery) => delivery.feedback === null).length,
      0,
    );
    waiting.set(report.cohortId, open);
  }

  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Bancada</p>
          <h1>Docente</h1>
          <p className="lede">Abra a turma para ver entrega, prova e chamada. O material se publica em Cursos.</p>
        </header>
        {cohorts === null ? <p className="empty">As turmas não respondem.</p> : null}
        {cohorts !== null && cohorts.length === 0 ? <p className="empty">Você ainda não ministra uma turma.</p> : null}
        {cohorts?.map((cohort) => (
          <section className="tree" key={cohort.id} aria-label={cohort.name}>
            <h2>
              <Link href={`/turmas/${cohort.id}`}>{cohort.name}</Link>
            </h2>
            <p className="course-name">{cohort.courseName}</p>
            <p className="tally">{waiting.get(cohort.id) ?? 0} entregas sem devolutiva</p>
            <p>
              <Link href={`/turmas/${cohort.id}`}>Abrir a grade</Link>
            </p>
          </section>
        ))}
      </div>
    </Shell>
  );
}

async function loadCohorts(token: string) {
  const read = await readJson(token, "/cohorts");
  if (read === null) {
    return null;
  }
  return cohortsFromBody(read.ok, read.body);
}
