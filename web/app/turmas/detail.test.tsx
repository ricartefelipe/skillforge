import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import type { CohortDetail } from "../../lib/cohort";
import { CohortDetailView } from "./detail";

const cohort: CohortDetail = {
  id: "t1",
  name: "Manhã",
  teacher: "docente",
  course: {
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
  students: null,
};

const noop = () => undefined;

afterEach(cleanup);

describe("CohortDetailView", () => {
  it("shows the lesson to the enrolled student and does not offer completion", () => {
    render(<CohortDetailView cohort={cohort} role="aluno" notice={null} enrollStudent={noop} />);

    expect(screen.getByRole("link", { name: "O que é" })).toHaveAttribute("href", "/catalogo/aulas/l1");
    expect(screen.queryByRole("button", { name: "Matricular" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /conclu/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Alunos" })).not.toBeInTheDocument();
  });

  it("lets administration enroll a student", () => {
    render(
      <CohortDetailView
        cohort={{ ...cohort, students: [] }}
        role="administracao"
        notice="Esse aluno já está na turma."
        enrollStudent={noop}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Esse aluno já está na turma.");
    expect(screen.getByRole("form", { name: "Matricular aluno" })).toBeInTheDocument();
    expect(screen.getByText("Nenhum aluno matriculado.")).toBeInTheDocument();
  });
});
