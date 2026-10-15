"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";

export async function createCourse(formData: FormData) {
  await sendJson("/courses", { name: field(formData, "name") }, back(formData));
}

export async function createModule(formData: FormData) {
  const courseId = field(formData, "courseId");
  await sendJson(`/courses/${courseId}/modules`, { name: field(formData, "name") }, back(formData));
}

export async function removeLesson(formData: FormData) {
  const lessonId = field(formData, "lessonId");
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  const page = back(formData);
  if (!token) {
    redirect("/login");
  }
  let response: Response;
  try {
    response = await fetch(`${apiUrl()}/lessons/${lessonId}`, {
      method: "DELETE",
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
  } catch {
    redirect(`${page}?erro=unavailable`);
  }
  if (!response.ok && response.status !== 204) {
    redirect(`${page}?erro=unavailable`);
  }
  redirect(page);
}

export async function reviseLesson(formData: FormData) {
  const lessonId = field(formData, "lessonId");
  const page = `/catalogo/aulas/${lessonId}`;
  const markdown = field(formData, "markdown");
  await send(
    `/lessons/${lessonId}`,
    JSON.stringify({
      title: field(formData, "title"),
      markdown: markdown.length === 0 ? null : markdown,
    }),
    { "content-type": "application/json" },
    page,
    "PUT",
  );
}

export async function addAsset(formData: FormData) {
  const lessonId = field(formData, "lessonId");
  const body = new FormData();
  body.set("name", field(formData, "name"));
  body.set("kind", field(formData, "kind"));
  const file = formData.get("file");
  if (file instanceof File && file.size > 0) {
    body.set("file", file);
  }
  await send(`/lessons/${lessonId}/assets`, body, {}, `/catalogo/aulas/${lessonId}`);
}

export async function createLesson(formData: FormData) {
  const moduleId = field(formData, "moduleId");
  const body = new FormData();
  body.set("title", field(formData, "title"));
  body.set("kind", field(formData, "kind"));
  const markdown = formData.get("markdown");
  if (typeof markdown === "string" && markdown.length > 0) {
    body.set("markdown", markdown);
  }
  const file = formData.get("file");
  if (file instanceof File && file.size > 0) {
    body.set("file", file);
  }
  const launchUrl = field(formData, "launchUrl");
  const secret = field(formData, "secret");
  if (launchUrl.length > 0) {
    body.set("launchUrl", launchUrl);
  }
  if (secret.length > 0) {
    body.set("secret", secret);
  }
  const authLogin = field(formData, "authLogin");
  if (authLogin.length > 0) {
    body.set("authLogin", authLogin);
  }
  await send(`/modules/${moduleId}/lessons`, body, {}, back(formData));
}

function field(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

function back(formData: FormData): string {
  const value = field(formData, "returnTo");
  if (value === "/aulas" || value === "/docente" || value === "/catalogo") {
    return value;
  }
  if (value.startsWith("/turmas/") && !value.slice("/turmas/".length).includes("/")) {
    return value;
  }
  return "/catalogo";
}

async function sendJson(path: string, payload: { name: string }, page: string) {
  await send(path, JSON.stringify(payload), { "content-type": "application/json" }, page);
}

async function send(
  path: string,
  body: BodyInit,
  headers: Record<string, string> = {},
  page = "/catalogo",
  method: "POST" | "PUT" = "POST",
) {
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    redirect("/login");
  }
  let response: Response;
  try {
    response = await fetch(`${apiUrl()}${path}`, {
      method,
      headers: { authorization: `Bearer ${token}`, ...headers },
      body,
      cache: "no-store",
    });
  } catch {
    redirect(`${page}?erro=unavailable`);
  }
  if (!response.ok) {
    redirect(`${page}?erro=${encodeURIComponent(await errorCode(response))}`);
  }
  redirect(page);
}

async function errorCode(response: Response): Promise<string> {
  try {
    const payload: unknown = await response.json();
    if (payload !== null && typeof payload === "object" && "error" in payload && typeof payload.error === "string") {
      return payload.error;
    }
  } catch {
    return "unavailable";
  }
  return "rejected";
}
