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

export async function removePractice(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const practiceId = field(formData, "practiceId");
  await del(`/cohorts/${cohortId}/practices/${practiceId}`, `/turmas/${cohortId}`);
}

export async function removeExam(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const examId = field(formData, "examId");
  await del(`/cohorts/${cohortId}/exams/${examId}`, `/turmas/${cohortId}`);
}

export async function withdrawNotice(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const announcementId = field(formData, "announcementId");
  await del(`/cohorts/${cohortId}/announcements/${announcementId}`, `/turmas/${cohortId}`);
}

export async function announce(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const page = field(formData, "returnTo") || `/turmas/${cohortId}`;
  await post(
    `/cohorts/${cohortId}/announcements`,
    { title: field(formData, "title"), body: field(formData, "body") },
    page,
  );
  redirect(page);
}

export async function callRoll(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const page = field(formData, "returnTo") || `/turmas/${cohortId}`;
  const present = formData.getAll("present").filter((value): value is string => typeof value === "string");
  const roster = field(formData, "roster").split(",").filter((name) => name.length > 0);
  const marks = roster.map((student) => ({ student, present: present.includes(student) }));
  await post(`/cohorts/${cohortId}/attendance`, { heldOn: field(formData, "heldOn"), marks }, page);
  redirect(page);
}

export async function openExam(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const page = field(formData, "returnTo") || `/turmas/${cohortId}`;
  const spec = field(formData, "spec");
  let payload: unknown = null;
  try {
    payload = JSON.parse(spec);
  } catch {
    redirect(`${page}?erro=invalid-exam`);
  }
  if (payload !== null && typeof payload === "object") {
    const dueAt = field(formData, "dueAt");
    payload = { ...payload, dueAt: dueAt.length === 0 ? null : new Date(dueAt).toISOString() };
  }
  await post(`/cohorts/${cohortId}/exams`, payload, page);
  redirect(page);
}

export async function takeExam(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const examId = field(formData, "examId");
  const page = `/turmas/${cohortId}`;
  const answers: { questionId: string; choiceId: string }[] = [];
  for (const [key, value] of formData.entries()) {
    if (key === "cohortId" || key === "examId" || typeof value !== "string") {
      continue;
    }
    answers.push({ questionId: key, choiceId: value });
  }
  await post(`/cohorts/${cohortId}/exams/${examId}/attempt`, { answers }, page);
  redirect(page);
}

export async function openPractice(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const page = `/turmas/${cohortId}`;
  const criterion = field(formData, "criterion");
  const points = Number(field(formData, "points"));
  const dueAt = field(formData, "dueAt");
  await post(
    `/cohorts/${cohortId}/practices`,
    {
      title: field(formData, "title"),
      prompt: field(formData, "prompt"),
      dueAt: dueAt.length === 0 ? null : new Date(dueAt).toISOString(),
      criteria: criterion.length === 0 ? [] : [{ name: criterion, points }],
    },
    page,
  );
  redirect(page);
}

export async function submitPractice(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const practiceId = field(formData, "practiceId");
  const page = `/turmas/${cohortId}`;
  await post(`/cohorts/${cohortId}/practices/${practiceId}/submission`, { text: field(formData, "text") }, page);
  redirect(page);
}

export async function returnPractice(formData: FormData) {
  const cohortId = field(formData, "cohortId");
  const practiceId = field(formData, "practiceId");
  const page = `/turmas/${cohortId}`;
  const marks: { criterionId: string; points: number }[] = [];
  for (const [key, value] of formData.entries()) {
    if (key.startsWith("mark:") && typeof value === "string") {
      marks.push({ criterionId: key.slice(5), points: Number(value) });
    }
  }
  const grade = marks.length === 0
    ? Number(field(formData, "grade"))
    : marks.reduce((sum, mark) => sum + mark.points, 0);
  await post(
    `/cohorts/${cohortId}/practices/${practiceId}/feedback`,
    { student: field(formData, "student"), text: field(formData, "text"), grade, marks },
    page,
  );
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

async function del(path: string, page: string) {
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    redirect("/login");
  }
  let response: Response;
  try {
    response = await fetch(`${apiUrl()}${path}`, {
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

async function post(
  path: string,
  payload: unknown,
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
