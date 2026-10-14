"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { ACCESS_COOKIE } from "../../../lib/cookies";
import { apiUrl } from "../../../lib/openid";

export async function openDoubt(formData: FormData) {
  const lessonId = field(formData, "lessonId");
  await post(lessonId, `/lessons/${lessonId}/doubts`, {
    title: field(formData, "title"),
    body: field(formData, "body"),
  });
}

export async function replyDoubt(formData: FormData) {
  const lessonId = field(formData, "lessonId");
  const doubtId = field(formData, "doubtId");
  await post(lessonId, `/lessons/${lessonId}/doubts/${doubtId}/replies`, {
    body: field(formData, "body"),
  });
}

function field(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

async function post(lessonId: string, path: string, payload: Record<string, string>) {
  const page = `/catalogo/aulas/${lessonId}`;
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    redirect("/login");
  }
  let response: Response;
  try {
    response = await fetch(`${apiUrl()}${path}`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(payload),
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
