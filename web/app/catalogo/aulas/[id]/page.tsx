import { cookies } from "next/headers";

import { loadSession } from "../../../../lib/access";
import { lessonFromBody } from "../../../../lib/catalog";
import { ACCESS_COOKIE } from "../../../../lib/cookies";
import { apiUrl } from "../../../../lib/openid";
import { Shell } from "../../../shell";
import { LessonView } from "../../lesson";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session || !access) {
    return (
      <Shell session={null}>
        <div className="page">
          <h1>Aula</h1>
          <p>Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const lesson = await loadLesson(access, id);
  if (!lesson) {
    return (
      <Shell session={session}>
        <div className="page">
          <h1>Aula</h1>
          <p>Não encontrei essa aula.</p>
        </div>
      </Shell>
    );
  }

  return (
    <Shell session={session}>
      <div className="page">
        <h1>{lesson.title}</h1>
        <LessonView lesson={lesson} />
      </div>
    </Shell>
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
