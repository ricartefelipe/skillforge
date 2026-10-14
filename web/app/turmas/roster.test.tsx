import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import type { Course } from "../../lib/catalog";
import type { CohortSummary } from "../../lib/cohort";
import { RosterView } from "./roster";

const course: Course = {
  id: "c1",
  name: "Forja",
  modules: [],
};

const morning: CohortSummary = {
  id: "t1",
  name: "Manhã",
  courseId: "c1",
  courseName: "Forja",
  teacher: "docente",
  students: ["aluno"],
};

const noop = () => undefined;

afterEach(cleanup);

describe("RosterView", () => {
  it("lets administration open a cohort and see who is enrolled", () => {
    render(
      <RosterView
        cohorts={[morning]}
        courses={[course]}
        role="administracao"
        notice="Já existe uma turma com esse nome neste curso."
        openCohort={noop}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Já existe uma turma com esse nome neste curso.");
    expect(screen.getByRole("form", { name: "Nova turma" })).toBeInTheDocument();
    const card = screen.getByRole("region", { name: "Manhã" });
    expect(within(card).getByRole("link", { name: "Manhã" })).toHaveAttribute("href", "/turmas/t1");
    expect(within(card).getByText("aluno")).toBeInTheDocument();
  });

  it("shows a student only the cohort, without the roster or the form", () => {
    render(
      <RosterView
        cohorts={[{ ...morning, students: null }]}
        courses={null}
        role="aluno"
        notice={null}
        openCohort={noop}
      />,
    );

    expect(screen.queryByRole("form", { name: "Nova turma" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Alunos" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Manhã" })).toBeInTheDocument();
  });

  it("does not pretend the list is empty when the cohorts cannot be read", () => {
    render(<RosterView cohorts={null} courses={null} role="docente" notice={null} openCohort={noop} />);

    expect(screen.getByText("As turmas não respondem.")).toBeInTheDocument();
    expect(screen.queryByText("Você ainda não ministra uma turma.")).not.toBeInTheDocument();
  });

  it("asks administration to create a course before the first cohort", () => {
    render(<RosterView cohorts={[]} courses={[]} role="administracao" notice={null} openCohort={noop} />);

    expect(screen.getByText("Abra um curso no catálogo antes da turma.")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma turma ainda.")).toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Nova turma" })).not.toBeInTheDocument();
  });
});
