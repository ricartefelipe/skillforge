"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { ACCESS_COOKIE } from "../../lib/cookies";
import { apiUrl } from "../../lib/openid";

export async function openCohort(formData: FormData) {
  const id = await post(
    "/cohorts",
    {
      name: field(formData, "name"),
      courseId: field(formData, "courseId"),
      teacher: field(formData, "teacher"),
    },
    "/turmas",
  );
  redirect(id ? `/turmas/${id}` : "/turmas");
}

export async function completeLesson(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const lessonId = field(formData, "lessonId");
  const page = `/turmas/${cohortId}`;
  await post(`/cohorts/${cohortId}/lessons/${lessonId}/completion`, {}, page);
  redirect(page);
}

export async function enrollStudent(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const page = `/turmas/${cohortId}`;
  await post(`/cohorts/${cohortId}/enrollments`, { student: field(formData, "student") }, page);
  redirect(page);
}

function field(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

async function post(
  path: string,
  payload: Record<string, string>,
  failurePage: string,
): Promise<string | null> {
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
    redirect(`${failurePage}?erro=unavailable`);
  }
  if (!response.ok) {
    redirect(`${failurePage}?erro=${encodeURIComponent(await errorCode(response))}`);
  }
  try {
    const created: unknown = await response.json();
    if (created !== null && typeof created === "object" && "id" in created && typeof created.id === "string") {
      return created.id;
    }
  } catch {
    return null;
  }
  return null;
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
