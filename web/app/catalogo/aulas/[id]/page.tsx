import { cookies } from "next/headers";

import { loadSession } from "../../../../lib/access";
import { catalogNotice, lessonFromBody } from "../../../../lib/catalog";
import { ACCESS_COOKIE } from "../../../../lib/cookies";
import { doubtsFromBody } from "../../../../lib/forum";
import { apiUrl } from "../../../../lib/openid";
import { Shell } from "../../../shell";
import { LessonView } from "../../lesson";
import { openDoubt, replyDoubt } from "../actions";
import { Forum } from "../../forum";

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
            <p className="kicker">Aula</p>
            <h1>Aula</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const lesson = await loadLesson(access, id);
  if (!lesson) {
    return (
      <Shell session={session}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Aula</p>
            <h1>Aula</h1>
          </header>
          <p className="empty">Não encontrei essa aula.</p>
        </div>
      </Shell>
    );
  }

  const query = await searchParams;
  const notice = catalogNotice(query.erro ?? null);
  return (
    <Shell session={session}>
      <div className="page">
        <header className="intro">
          <p className="kicker">Aula</p>
          <h1>{lesson.title}</h1>
        </header>
        {notice ? <p role="alert">{notice}</p> : null}
        <LessonView lesson={lesson} />
        <Forum lessonId={id} doubts={await loadDoubts(access, id)} openDoubt={openDoubt} replyDoubt={replyDoubt} />
      </div>
    </Shell>
  );
}

async function loadDoubts(token: string, id: string) {
  try {
    const response = await fetch(`${apiUrl()}/lessons/${id}/doubts`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return doubtsFromBody(response.ok, body);
  } catch {
    return null;
  }
}

async function loadLesson(token: string, id: string) {
  try {
    const response = await fetch(`${apiUrl()}/lessons/${id}`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return lessonFromBody(response.ok, body);
  } catch {
    return null;
  }
}
