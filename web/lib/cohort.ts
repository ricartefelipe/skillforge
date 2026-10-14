import { courseFrom, type Course } from "./catalog";

export type CohortSummary = {
  id: string;
  name: string;
  courseId: string;
  courseName: string;
  teacher: string;
  students: string[] | null;
};

export type CohortDetail = {
  id: string;
  name: string;
  teacher: string;
  course: Course;
  students: string[] | null;
};

type Roster = { present: false } | { present: true; students: string[] };

export function canManageCohorts(role: string): boolean {
  return role === "administracao";
}

export function cohortNotice(code: string | null): string | null {
  switch (code) {
    case "blank-cohort":
      return "A turma precisa de um nome.";
    case "blank-teacher":
      return "A turma precisa de um docente.";
    case "blank-student":
      return "A matrícula precisa da conta do aluno.";
    case "duplicate-cohort":
      return "Já existe uma turma com esse nome neste curso.";
    case "already-enrolled":
      return "Esse aluno já está na turma.";
    case "too-large":
      return "Passa do limite.";
    case "not-found":
      return "Não encontrei esse item.";
    case "unavailable":
      return "As turmas não respondem.";
    case null:
    case "":
      return null;
    default:
      return "Não foi possível guardar.";
  }
}

export function emptyCohorts(role: string): string {
  switch (role) {
    case "aluno":
      return "Você ainda não está em uma turma.";
    case "docente":
      return "Você ainda não ministra uma turma.";
    case "administracao":
      return "Nenhuma turma ainda.";
    default:
      return "Nenhuma turma ainda.";
  }
}

export function cohortsFromBody(httpOk: boolean, body: unknown): CohortSummary[] | null {
  if (!httpOk || !Array.isArray(body)) {
    return null;
  }
  const cohorts: CohortSummary[] = [];
  for (const item of body) {
    const cohort = summaryFrom(item);
    if (cohort === null) {
      return null;
    }
    cohorts.push(cohort);
  }
  return cohorts;
}

export function cohortDetailFromBody(httpOk: boolean, body: unknown): CohortDetail | null {
  if (!httpOk || body === null || typeof body !== "object") {
    return null;
  }
  const id = text(body, "id");
  const name = text(body, "name");
  const teacher = text(body, "teacher");
  const courseValue = "course" in body ? body.course : undefined;
  const course = courseFrom(courseValue);
  const roster = rosterFrom(body);
  if (id === null || name === null || teacher === null || course === null || roster === null) {
    return null;
  }
  return {
    id,
    name,
    teacher,
    course,
    students: roster.present ? roster.students : null,
  };
}

function summaryFrom(value: unknown): CohortSummary | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const id = text(value, "id");
  const name = text(value, "name");
  const courseId = text(value, "courseId");
  const courseName = text(value, "courseName");
  const teacher = text(value, "teacher");
  const roster = rosterFrom(value);
  if (
    id === null ||
    name === null ||
    courseId === null ||
    courseName === null ||
    teacher === null ||
    roster === null
  ) {
    return null;
  }
  return {
    id,
    name,
    courseId,
    courseName,
    teacher,
    students: roster.present ? roster.students : null,
  };
}

function rosterFrom(value: object): Roster | null {
  if (!("students" in value)) {
    return { present: false };
  }
  const found = value.students;
  if (!Array.isArray(found)) {
    return null;
  }
  const students: string[] = [];
  for (const item of found) {
    if (typeof item !== "string" || item.trim().length === 0) {
      return null;
    }
    students.push(item);
  }
  return { present: true, students };
}

function text(value: object, key: string): string | null {
  const found = (value as Record<string, unknown>)[key];
  if (typeof found !== "string" || found.trim().length === 0) {
    return null;
  }
  return found;
}
