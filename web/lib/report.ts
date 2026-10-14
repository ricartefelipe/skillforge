import { readJson } from "./api-read";
import type { Practice } from "./cohort";
import { practicesFromObject } from "./cohort";

export type Report = {
  cohortId: string;
  cohortName: string;
  courseName: string;
  practices: Practice[];
};

export async function loadReports(token: string): Promise<Report[] | null> {
  const read = await readJson(token, "/grades");
  if (read === null) {
    return null;
  }
  return reportsFromBody(read.ok, read.body);
}

export function reportsFromBody(httpOk: boolean, body: unknown): Report[] | null {
  if (!httpOk || !Array.isArray(body)) {
    return null;
  }
  const reports: Report[] = [];
  for (const item of body) {
    if (item === null || typeof item !== "object") {
      return null;
    }
    const cohortId = text(item, "cohortId");
    const cohortName = text(item, "cohortName");
    const courseName = text(item, "courseName");
    const practices = practicesFromObject(item);
    if (cohortId === null || cohortName === null || courseName === null || practices === null) {
      return null;
    }
    reports.push({ cohortId, cohortName, courseName, practices });
  }
  return reports;
}

function text(value: object, key: string): string | null {
  const found = (value as Record<string, unknown>)[key];
  if (typeof found !== "string" || found.trim().length === 0) {
    return null;
  }
  return found;
}
