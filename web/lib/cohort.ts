import { courseFrom, type Course } from "./catalog";

export type StudentProgress = {
  student: string;
  completed: number;
  total: number;
};

export type CohortSummary = {
  id: string;
  name: string;
  courseId: string;
  courseName: string;
  teacher: string;
  students: StudentProgress[] | null;
  completed: number | null;
  total: number | null;
};

export type CohortDetail = {
  id: string;
  name: string;
  teacher: string;
  course: Course;
  students: StudentProgress[] | null;
  completed: number | null;
  total: number | null;
};

type Roster = { present: false } | { present: true; students: StudentProgress[] };

export function progressLabel(completed: number, total: number): string {
  return `${completed} de ${total} aulas`;
}

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
  const tally = tallyFrom(body);
  if (id === null || name === null || teacher === null || course === null || roster === null || tally === null) {
    return null;
  }
  return {
    id,
    name,
    teacher,
    course,
    students: roster.present ? roster.students : null,
    completed: tally === undefined ? null : tally.completed,
    total: tally === undefined ? null : tally.total,
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
  const tally = tallyFrom(value);
  if (
    id === null ||
    name === null ||
    courseId === null ||
    courseName === null ||
    teacher === null ||
    roster === null ||
    tally === null
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
    completed: tally === undefined ? null : tally.completed,
    total: tally === undefined ? null : tally.total,
  };
}

function rosterFrom(value: object): Roster | null {
  if (!("students" in value)) {
    return { present: false };
  }
  const found = (value as Record<string, unknown>).students;
  if (!Array.isArray(found)) {
    return null;
  }
  const students: StudentProgress[] = [];
  for (const item of found) {
    const member = memberFrom(item);
    if (member === null) {
      return null;
    }
    students.push(member);
  }
  return { present: true, students };
}

function memberFrom(value: unknown): StudentProgress | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const student = text(value, "student");
  const completed = count(value, "completed");
  const total = count(value, "total");
  if (student === null || completed === null || total === null) {
    return null;
  }
  return { student, completed, total };
}

function tallyFrom(value: object): { completed: number; total: number } | null | undefined {
  if (!("completed" in value) && !("total" in value)) {
    return undefined;
  }
  const completed = count(value, "completed");
  const total = count(value, "total");
  if (completed === null || total === null) {
    return null;
  }
  return { completed, total };
}

function count(value: object, key: string): number | null {
  const found = (value as Record<string, unknown>)[key];
  if (typeof found !== "number" || !Number.isInteger(found) || found < 0) {
    return null;
  }
  return found;
}

function text(value: object, key: string): string | null {
  const found = (value as Record<string, unknown>)[key];
  if (typeof found !== "string" || found.trim().length === 0) {
    return null;
  }
  return found;
}
