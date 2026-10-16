import { cookies } from "next/headers";

import { loadSession } from "../../lib/access";
import { canCompose, catalogNotice, coursesFromBody, narrowCourses } from "../../lib/catalog";
import { ACCESS_COOKIE } from "../../lib/cookies";
import { readIds, readJson } from "../../lib/api-read";
import { apiUrl } from "../../lib/openid";
import { Shell } from "../shell";
import { createCourse, createLesson, createModule } from "./actions";
import { CatalogView } from "./catalog";

export default async function Page({ searchParams }: { searchParams: Promise<{ erro?: string; q?: string }> }) {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  if (!session) {
    return (
      <Shell session={null}>
        <div className="page">
          <header className="intro">
            <p className="kicker">Disponíveis</p>
            <h1>Cursos</h1>
          </header>
          <p className="empty">Entre com a sua conta.</p>
        </div>
      </Shell>
    );
  }

  const params = await searchParams;
  const token = access ?? "";
  const studying = session.role !== "administracao";
  return (
    <Shell session={session}>
      <CatalogView
        courses={narrowCourses(await loadCourses(token, studying), params.q ?? "")}
        query={params.q ?? ""}
        canCompose={canCompose(session.role)}
        writableCourseIds={canCompose(session.role) ? [] : await readIds(token, "/courses/teaching")}
        notice={catalogNotice(params.erro ?? null)}
        createCourse={createCourse}
        createModule={createModule}
        createLesson={createLesson}
        lede={courseLede(session.role)}
        emptyMessage={session.role === "aluno" ? "Você ainda não está em um curso." : "Nenhum curso ainda."}
      />
    </Shell>
  );
}

function courseLede(role: string): string {
  switch (role) {
    case "aluno":
      return "Aqui está o material das suas turmas. Para entregar, fazer prova ou ver a chamada, abra a turma.";
    case "docente":
      return "Aqui você publica as aulas. A entrega e a nota acontecem na turma.";
    case "administracao":
      return "Aqui você cria o curso e publica as aulas. A matrícula e o docente ficam em Turmas.";
    default:
      return "O curso é o material. A entrega, a prova e a chamada ficam na turma.";
  }
}

async function loadCourses(token: string, studying: boolean) {
  const read = await readJson(token, studying ? "/courses/curriculum" : "/courses");
  if (read === null) {
    return null;
  }
  return coursesFromBody(read.ok, read.body);
}
