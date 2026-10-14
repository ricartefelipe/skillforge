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
            <p className="kicker">Notas</p>
            <h1>Boletim</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const reports = await loadReports(access);
  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Notas</p>
          <h1>Boletim</h1>
        </header>
        {reports === null ? <p className="empty">O boletim não responde.</p> : null}
        {reports !== null && reports.length === 0 ? <p className="empty">Nenhuma turma no boletim.</p> : null}
        {reports?.map((report) => {
          const grades = [
            ...report.practices.flatMap((practice) =>
              practice.deliveries
                .filter((delivery) => delivery.grade !== null)
                .map((delivery) => delivery.grade as number),
            ),
            ...report.exams.flatMap((exam) => exam.scores.map((score) => score.grade)),
          ];
          const average = grades.length === 0 ? null : grades.reduce((sum, grade) => sum + grade, 0) / grades.length;
          return (
            <section className="tree" key={report.cohortId} aria-label={report.cohortName}>
              <h2>
                <Link href={`/turmas/${report.cohortId}`}>{report.cohortName}</Link>
              </h2>
              <p className="course-name">{report.courseName}</p>
              {average === null ? <p className="empty">Sem nota ainda.</p> : <p className="tally">Média {average.toFixed(1)}</p>}
              <ul>
                {report.exams.map((exam) => (
                  <li className="person" key={exam.id}>
                    <span>{exam.title}</span>
                    <span className="tally">
                      {exam.scores.length === 0
                        ? "Sem prova feita"
                        : exam.scores.map((score) => `${score.student} ${score.grade}`).join(", ")}
                    </span>
                  </li>
                ))}
                {report.practices.map((practice) => (
                  <li className="person" key={practice.id}>
                    <span>{practice.title}</span>
                    <span className="tally">
                      {practice.deliveries.length === 0
                        ? "Sem entrega"
                        : practice.deliveries
                            .map((delivery) =>
                              delivery.grade === null
                                ? `${delivery.student} sem nota`
                                : `${delivery.student} ${delivery.grade}`,
                            )
                            .join(", ")}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    </Shell>
  );
}

