import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";
import { Shell } from "../shell";
import { markRead } from "../turmas/actions";

export default async function Page() {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Recados</p>
            <h1>Recados</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const notes = await loadInbox(access);
  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Recados</p>
          <h1>Recados</h1>
        </header>
        {notes.length === 0 ? <p className="empty">Nenhum recado.</p> : null}
        {notes.map((note) => (
          <section key={note.id} className="module">
            <p>{note.body}</p>
            {note.read ? (
              <p className="meta">Lido</p>
            ) : (
              <form action={markRead}>
                <input type="hidden" name="id" value={note.id} />
                <button type="submit">Marcar como lido</button>
              </form>
            )}
          </section>
        ))}
      </div>
    </Shell>
  );
}

async function loadInbox(token: string) {
  try {
    const response = await fetch(`${apiUrl()}/inbox`, {
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
      if (typeof record.id !== "string" || typeof record.body !== "string" || typeof record.read !== "boolean") {
        return [];
      }
      return [{ id: record.id, body: record.body, read: record.read }];
    });
  } catch {
    return [];
  }
}
