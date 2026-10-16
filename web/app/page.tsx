import { cookies } from "next/headers";

import { loadSession } from "../lib/access";
import { courseOfLesson, coursesFromBody } from "../lib/catalog";
import { ACCESS_COOKIE } from "../lib/cookies";
import { readJson } from "../lib/api-read";
import { apiUrl } from "../lib/openid";
import { SkillforgeHome } from "./home";

export default async function Page() {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  return <SkillforgeHome session={session} resume={access && session ? await nextLesson(access) : null} />;
}

async function nextLesson(token: string) {
  const read = await readJson(token, "/resume");
  if (read === null || !read.ok || read.body === null || typeof read.body !== "object") {
    return null;
  }
  const record = read.body as Record<string, unknown>;
  if (typeof record.lessonId !== "string" || typeof record.title !== "string") {
    return null;
  }
  const course = courseOfLesson(await loadCourses(token), record.lessonId);
  return {
    href: `/catalogo/aulas/${record.lessonId}`,
    title: record.title,
    courseName: course?.name ?? null,
  };
}

async function loadCourses(token: string) {
  const read = await readJson(token, "/courses/curriculum");
  if (read === null) {
    return [];
  }
  return coursesFromBody(read.ok, read.body) ?? [];
}
