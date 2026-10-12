import { describe, expect, it } from "vitest";

import { canCompose, catalogNotice, coursesFromBody, lessonFromBody } from "./catalog";

describe("catalog", () => {
  it("lets only administration compose", () => {
    expect(canCompose("administracao")).toBe(true);
    expect(canCompose("aluno")).toBe(false);
    expect(canCompose("docente")).toBe(false);
  });

  it("names the refusal in Portuguese", () => {
    expect(catalogNotice("not-pdf")).toBe("O arquivo não é um PDF.");
    expect(catalogNotice("duplicate-course")).toBe("Já existe um curso com esse nome.");
    expect(catalogNotice(null)).toBeNull();
    expect(catalogNotice("nope")).toBe("Não foi possível guardar.");
  });

  it("reads a course tree and rejects a broken one", () => {
    const body = [
      {
        id: "c1",
        name: "Forja",
        modules: [
          {
            id: "m1",
            name: "Fundamentos",
            lessons: [{ id: "l1", title: "O que é", kind: "markdown" }],
          },
        ],
      },
    ];

    expect(coursesFromBody(true, body)).toEqual(body);
    expect(coursesFromBody(false, body)).toBeNull();
    expect(coursesFromBody(true, [{ id: "c1", name: "Forja", modules: [{ id: "m1" }] }])).toBeNull();
  });

  it("reads markdown only when the text is present", () => {
    expect(
      lessonFromBody(true, { id: "l1", title: "O que é", kind: "markdown", markdown: "Oficina" }),
    ).toEqual({ id: "l1", title: "O que é", kind: "markdown", markdown: "Oficina" });
    expect(lessonFromBody(true, { id: "l1", title: "O que é", kind: "markdown", markdown: " " })).toBeNull();
    expect(lessonFromBody(true, { id: "l1", title: "Notas", kind: "pdf" })).toEqual({
      id: "l1",
      title: "Notas",
      kind: "pdf",
    });
  });
});
