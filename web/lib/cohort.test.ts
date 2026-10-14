import { describe, expect, it } from "vitest";

import { canManageCohorts, cohortDetailFromBody, cohortNotice, cohortsFromBody, emptyCohorts } from "./cohort";

describe("cohort", () => {
  it("lets only administration open a cohort", () => {
    expect(canManageCohorts("administracao")).toBe(true);
    expect(canManageCohorts("aluno")).toBe(false);
    expect(canManageCohorts("docente")).toBe(false);
  });

  it("names the refusal in Portuguese", () => {
    expect(cohortNotice("blank-teacher")).toBe("A turma precisa de um docente.");
    expect(cohortNotice("already-enrolled")).toBe("Esse aluno já está na turma.");
    expect(cohortNotice(null)).toBeNull();
    expect(cohortNotice("nope")).toBe("Não foi possível guardar.");
  });

  it("tells each role when the list is empty", () => {
    expect(emptyCohorts("aluno")).toBe("Você ainda não está em uma turma.");
    expect(emptyCohorts("docente")).toBe("Você ainda não ministra uma turma.");
    expect(emptyCohorts("administracao")).toBe("Nenhuma turma ainda.");
  });

  it("reads a cohort list and hides a missing roster", () => {
    const body = [
      {
        id: "t1",
        name: "Manhã",
        courseId: "c1",
        courseName: "Forja",
        teacher: "docente",
      },
    ];

    expect(cohortsFromBody(true, body)).toEqual([
      { ...body[0], students: null, completed: null, total: null },
    ]);
    expect(cohortsFromBody(false, body)).toBeNull();
    expect(cohortsFromBody(true, [{ id: "t1", name: "Manhã" }])).toBeNull();
  });

  it("reads the course lessons and keeps the roster only when it is present", () => {
    const course = {
      id: "c1",
      name: "Forja",
      modules: [
        {
          id: "m1",
          name: "Fundamentos",
          lessons: [{ id: "l1", title: "O que é", kind: "markdown" }],
        },
      ],
    };

    expect(
      cohortDetailFromBody(true, {
        id: "t1",
        name: "Manhã",
        teacher: "docente",
        course,
        students: [{ student: "aluno", completed: 1, total: 3 }],
        completed: 1,
        total: 3,
      }),
    ).toEqual({
      id: "t1",
      name: "Manhã",
      teacher: "docente",
      course,
      students: [{ student: "aluno", completed: 1, total: 3 }],
      completed: 1,
      total: 3,
      practices: [],
      exams: [],
    });

    expect(
      cohortDetailFromBody(true, {
        id: "t1",
        name: "Manhã",
        teacher: "docente",
        course,
      })?.students,
    ).toBeNull();

    expect(cohortDetailFromBody(true, { id: "t1", name: "Manhã", teacher: "docente" })).toBeNull();
  });
});
