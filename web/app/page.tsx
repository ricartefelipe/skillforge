import { cookies } from "next/headers";

import { loadSession } from "../lib/access";
import { coursesFromBody } from "../lib/catalog";
import { ACCESS_COOKIE } from "../lib/cookies";
import { readJson } from "../lib/api-read";
import { apiUrl } from "../lib/openid";
import { SkillforgeHome } from "./home";

export default async function Page() {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  return <SkillforgeHome session={session} resume={access && session ? await firstLesson(access) : null} />;
}

async function firstLesson(token: string) {
  const read = await readJson(token, "/courses/curriculum");
  if (read === null) {
    return null;
  }
  const courses = coursesFromBody(read.ok, read.body);
  const lesson = courses?.flatMap((course) => course.modules.flatMap((module) => module.lessons))[0];
  if (!lesson) {
    return null;
  }
  return { href: `/catalogo/aulas/${lesson.id}`, title: lesson.title };
}
