"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";

export async function createCourse(formData: FormData) {
  await sendJson("/courses", { name: String(formData.get("name") ?? "") });
}

export async function createModule(formData: FormData) {
  const courseId = String(formData.get("courseId") ?? "");
  await sendJson(`/courses/${courseId}/modules`, { name: String(formData.get("name") ?? "") });
}

export async function createLesson(formData: FormData) {
  const moduleId = String(formData.get("moduleId") ?? "");
  const body = new FormData();
  body.set("title", String(formData.get("title") ?? ""));
  body.set("kind", String(formData.get("kind") ?? ""));
  const markdown = formData.get("markdown");
  if (typeof markdown === "string" && markdown.length > 0) {
    body.set("markdown", markdown);
  }
  const file = formData.get("file");
  if (file instanceof File && file.size > 0) {
    body.set("file", file);
  }
  await send(`/modules/${moduleId}/lessons`, body);
}

async function sendJson(path: string, payload: { name: string }) {
  await send(path, JSON.stringify(payload), { "content-type": "application/json" });
}

async function send(path: string, body: BodyInit, headers: Record<string, string> = {}) {
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    redirect("/login");
  }
  let response: Response;
  try {
    response = await fetch(`${apiUrl()}${path}`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, ...headers },
      body,
      cache: "no-store",
    });
  } catch {
    redirect("/catalogo?erro=unavailable");
  }
  if (!response.ok) {
    redirect(`/catalogo?erro=${encodeURIComponent(await errorCode(response))}`);
  }
  redirect("/catalogo");
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
