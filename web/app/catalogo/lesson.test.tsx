import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { LessonView } from "./lesson";

afterEach(cleanup);

describe("LessonView", () => {
  it("renders markdown as text and does not keep a script element", () => {
    const { container } = render(
      <LessonView
        lesson={{
          id: "l1",
          title: "O que é",
          kind: "markdown",
          markdown: "Oficina\n\n<script>alert(1)</script>",
        }}
      />,
    );

    expect(screen.getByText("Oficina")).toBeInTheDocument();
    expect(container.querySelector("script")).toBeNull();
    expect(screen.getByText("<script>alert(1)</script>")).toBeInTheDocument();
  });

  it("points a pdf at the lesson file and plays a video from the same place", () => {
    const { rerender } = render(<LessonView lesson={{ id: "l2", title: "Notas", kind: "pdf" }} />);

    expect(screen.getByRole("link", { name: "Abrir o PDF" })).toHaveAttribute("href", "/catalogo/aulas/l2/arquivo");
    expect(screen.getByTitle("PDF da aula")).toHaveAttribute("src", "/catalogo/aulas/l2/arquivo");

    rerender(<LessonView lesson={{ id: "l3", title: "Demo", kind: "video" }} />);

    expect(screen.getByLabelText("Vídeo da aula")).toHaveAttribute("src", "/catalogo/aulas/l3/arquivo");
  });
});
