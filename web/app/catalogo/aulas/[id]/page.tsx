import { cookies } from "next/headers";

import { loadSession } from "../../../../lib/access";
import { lessonFromBody } from "../../../../lib/catalog";
import { ACCESS_COOKIE } from "../../../../lib/cookies";
import { apiUrl } from "../../../../lib/openid";
import { LessonView } from "../../lesson";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <main className="floor catalog">
        <p className="mark">Skillforge</p>
        <h1>Aula</h1>
        <a href="/login">Entrar</a>
      </main>
    );
  }

  const lesson = await loadLesson(access, id);
  if (!lesson) {
    return (
      <main className="floor catalog">
        <p className="mark">Skillforge</p>
        <h1>Aula</h1>
        <p>Não encontrei essa aula.</p>
        <a className="back" href="/catalogo">
          Voltar
        </a>
      </main>
    );
  }

  return (
    <main className="floor catalog">
      <p className="mark">Skillforge</p>
      <h1>{lesson.title}</h1>
      <a className="back" href="/catalogo">
        Voltar
      </a>
      <LessonView lesson={lesson} />
    </main>
  );
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
