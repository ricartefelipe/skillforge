import { describe, expect, it } from "vitest";

import { deskOf } from "./desk";

describe("deskOf", () => {
  it("gives the student learning, the teacher corrections, and the office the school", () => {
    const student = deskOf("aluno");
    const teacher = deskOf("docente");
    const office = deskOf("administracao");

    expect(student.title).toBe("Continuar aprendendo");
    expect(student.nav.map((link) => link.label)).toContain("Boletim");
    expect(student.nav.map((link) => link.label)).not.toContain("Correções");

    expect(teacher.title).toBe("Suas turmas");
    expect(teacher.nav.map((link) => link.label)).toContain("Correções");
    expect(teacher.nav.map((link) => link.label)).not.toContain("Boletim");

    expect(office.title).toBe("Administrar a escola");
    expect(office.shortcuts.map((link) => link.label)).toEqual(["Abrir turma", "Criar curso", "Correções"]);
    expect(office.nav.map((link) => link.label)).not.toContain("Boletim");
  });
});
