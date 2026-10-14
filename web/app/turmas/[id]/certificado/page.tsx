import { cookies } from "next/headers";

import { loadSession } from "../../../../lib/access";
import { cohortNotice } from "../../../../lib/cohort";
import { ACCESS_COOKIE } from "../../../../lib/cookies";
import { apiUrl } from "../../../../lib/openid";
import { Shell } from "../../../shell";

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
          <h1>Certificado</h1>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const query = await searchParams;
  const certificate = await loadCertificate(access, id);
  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Conclusão</p>
          <h1>Certificado</h1>
        </header>
        {query.erro ? <p role="alert">{cohortNotice(query.erro)}</p> : null}
        {certificate === null ? (
          <p className="empty">Ainda não há certificado. É preciso média 7 e, se houver chamada, 75% de presença.</p>
        ) : (
          <section className="tree" aria-label="Certificado">
            <p className="meta">Certificamos que</p>
            <h2>{certificate.student}</h2>
            <p>concluiu {certificate.courseName}, turma {certificate.cohortName}.</p>
            <p className="tally">Média {certificate.average}</p>
            <p className="meta">Emitido em {certificate.issuedOn}</p>
          </section>
        )}
      </div>
    </Shell>
  );
}

async function loadCertificate(token: string, id: string) {
  try {
    const response = await fetch(`${apiUrl()}/cohorts/${id}/certificate`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!response.ok) {
      return null;
    }
    const body: unknown = await response.json();
    if (body === null || typeof body !== "object") {
      return null;
    }
    const record = body as Record<string, unknown>;
    if (
      typeof record.student !== "string" ||
      typeof record.courseName !== "string" ||
      typeof record.cohortName !== "string" ||
      typeof record.average !== "string" ||
      typeof record.issuedOn !== "string"
    ) {
      return null;
    }
    return {
      student: record.student,
      courseName: record.courseName,
      cohortName: record.cohortName,
      average: record.average,
      issuedOn: record.issuedOn,
    };
  } catch {
    return null;
  }
}
