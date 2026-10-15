import Link from "next/link";
import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { catalogNotice } from "../../lib/catalog";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";
import { Shell } from "../shell";

export default async function Page({ searchParams }: { searchParams: Promise<{ q?: string; erro?: string }> }) {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  const query = await searchParams;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Busca</p>
            <h1>Busca</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const notice = catalogNotice(query.erro ?? null);
  const hits = await search(access, query.q ?? "");
  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Busca</p>
          <h1>Busca</h1>
        </header>
        <form className="compose" action="/busca" method="get" aria-label="Buscar">
          <label>
            Buscar no acervo
            <input name="q" defaultValue={query.q ?? ""} />
          </label>
          <button type="submit">Buscar</button>
        </form>
        {notice ? <p role="alert">{notice}</p> : null}
        {hits.length === 0 ? <p className="empty">Nada com esse nome.</p> : null}
        <ul>
          {hits.map((hit) => (
            <li key={`${hit.kind}-${hit.href}-${hit.title}`}>
              <Link href={hit.href}>{hit.title}</Link>
              <span className="kind">{label(hit.kind)}</span>
            </li>
          ))}
        </ul>
      </div>
    </Shell>
  );
}

function label(kind: string) {
  switch (kind) {
    case "aula":
      return "Aula";
    case "pratica":
      return "Prática";
    case "prova":
      return "Prova";
    default:
      return kind;
  }
}

async function search(token: string, query: string) {
  if (query.trim().length < 2) {
    return [];
  }
  try {
    const response = await fetch(`${apiUrl()}/search?q=${encodeURIComponent(query)}`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!response.ok) {
      return [];
    }
    const body: unknown = await response.json();
    if (!Array.isArray(body)) {
      return [];
    }
    return body.flatMap((item) => {
      if (item === null || typeof item !== "object") {
        return [];
      }
      const record = item as Record<string, unknown>;
      if (typeof record.kind !== "string" || typeof record.title !== "string" || typeof record.href !== "string") {
        return [];
      }
      return [{ kind: record.kind, title: record.title, href: record.href }];
    });
  } catch {
    return [];
  }
}
