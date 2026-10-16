import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import type { Course } from "../../lib/catalog";
import { CatalogView } from "./catalog";

const course: Course = {
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

const noop = () => undefined;

afterEach(cleanup);

describe("CatalogView", () => {
  it("shows the tree and the compose forms for administration", () => {
    render(
      <CatalogView
        courses={[course]}
        canCompose
        writableCourseIds={[]}
        notice="Já existe um curso com esse nome."
        createCourse={noop}
        createModule={noop}
        createLesson={noop}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Já existe um curso com esse nome.");
    expect(screen.getByRole("form", { name: "Novo curso" })).toBeInTheDocument();
    const tree = screen.getByRole("region", { name: "Forja" });
    expect(within(tree).getByRole("link", { name: "O que é" })).toHaveAttribute("href", "/catalogo/aulas/l1");
    expect(within(tree).getByRole("form", { name: "Novo material" })).toBeInTheDocument();
    expect(within(tree).getByText("Unidade 1")).toBeInTheDocument();
  });

  it("names the course in progress when the student has more than one", () => {
    render(
      <CatalogView
        courses={[]}
        canCompose={false}
        writableCourseIds={[]}
        notice={null}
        createCourse={noop}
        createModule={noop}
        createLesson={noop}
        studies={[
          {
            courseName: "Forja",
            cohortName: "Manhã",
            cohortHref: "/turmas/a",
            progress: "1 de 2 aulas",
            examReady: false,
            current: false,
            continueHref: null,
            continueTitle: null,
          },
          {
            courseName: "Oficina de competências",
            cohortName: "Turma Manhã 2026",
            cohortHref: "/turmas/b",
            progress: "3 de 13 aulas",
            examReady: false,
            current: true,
            continueHref: "/catalogo/aulas/v1",
            continueTitle: "O que é uma competência",
          },
          {
            courseName: "Cerâmica",
            cohortName: "Noite",
            cohortHref: "/turmas/c",
            progress: "21 de 21 aulas",
            examReady: true,
            current: false,
            continueHref: null,
            continueTitle: null,
          },
        ]}
      />,
    );

    const current = screen.getByRole("region", { name: "Oficina de competências" });
    expect(within(current).getByText("Em andamento")).toBeInTheDocument();
    expect(within(current).getByText("Turma Manhã 2026")).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Forja" })).getByText("Matriculado")).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Cerâmica" })).getByRole("link", { name: "Fazer a prova" }))
      .toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Forja" })).queryByRole("link", { name: "Fazer a prova" }))
      .not.toBeInTheDocument();
  });

  it("hides compose forms from a student and does not call an empty catalog missing", () => {
    render(
      <CatalogView courses={[]} canCompose={false} writableCourseIds={[]} notice={null} createCourse={noop} createModule={noop} createLesson={noop} />,
    );

    expect(screen.getByText("Nenhum curso ainda.")).toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Novo curso" })).not.toBeInTheDocument();
    expect(screen.queryByText("O catálogo não responde.")).not.toBeInTheDocument();
  });

  it("does not pretend the catalog is empty when it cannot be read", () => {
    render(
      <CatalogView courses={null} canCompose={false} writableCourseIds={[]} notice={null} createCourse={noop} createModule={noop} createLesson={noop} />,
    );

    expect(screen.getByText("O catálogo não responde.")).toBeInTheDocument();
    expect(screen.queryByText("Nenhum curso ainda.")).not.toBeInTheDocument();
  });

  it("asks for a file when the lesson is a video and for text when it is markdown", () => {
    render(
      <CatalogView
        courses={[course]}
        canCompose
        writableCourseIds={[]}
        notice={null}
        createCourse={noop}
        createModule={noop}
        createLesson={noop}
      />,
    );

    const form = screen.getByRole("form", { name: "Novo material" });
    expect(within(form).getByRole("textbox", { name: "Texto" })).toBeInTheDocument();

    fireEvent.change(within(form).getByRole("combobox", { name: "Material" }), { target: { value: "video" } });

    expect(within(form).queryByRole("textbox", { name: "Texto" })).not.toBeInTheDocument();
    expect(within(form).getByLabelText("Arquivo")).toBeInTheDocument();
  });
});
