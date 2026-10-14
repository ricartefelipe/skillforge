import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SkillforgeHome } from "./home";

function probe(status: string, ok = status === "UP") {
  return {
    ok,
    json: async () => ({ status }),
  };
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("SkillforgeHome", () => {
  it("shows the environment standing when both probes are up", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string) => (path.endsWith("readiness") ? probe("UP") : probe("UP"))),
    );

    render(<SkillforgeHome session={null} />);

    expect(await screen.findByRole("heading", { level: 1, name: "O ambiente está de pé." })).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Vida" })).getByText("no ar")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Prontidão" })).getByText("pronta")).toBeInTheDocument();
  });

  it("keeps liveness visible when readiness refuses traffic", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string) => (path.endsWith("readiness") ? probe("DOWN", false) : probe("UP"))),
    );

    render(<SkillforgeHome session={null} />);

    expect(
      await screen.findByRole("heading", { level: 1, name: "O processo vive. Ainda não atende." }),
    ).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Vida" })).getByText("no ar")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Prontidão" })).getByText("indisponível")).toBeInTheDocument();
  });

  it("does not claim the environment is up when the probes cannot be read", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("connection refused");
      }),
    );

    render(<SkillforgeHome session={null} />);

    expect(await screen.findByRole("heading", { level: 1, name: "O ambiente não responde." })).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Vida" })).getByText("fora do ar")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Prontidão" })).getByText("indisponível")).toBeInTheDocument();
  });

  it("reads the probes again when asked", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(probe("UP"))
      .mockResolvedValueOnce(probe("UP"))
      .mockResolvedValueOnce(probe("UP"))
      .mockResolvedValueOnce(probe("DOWN", false));
    vi.stubGlobal("fetch", fetchMock);

    render(<SkillforgeHome session={null} />);
    expect(await screen.findByRole("heading", { level: 1, name: "O ambiente está de pé." })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Consultar de novo" }));

    expect(
      await screen.findByRole("heading", { level: 1, name: "O processo vive. Ainda não atende." }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("asks an anonymous person to enter", () => {
    vi.stubGlobal("fetch", vi.fn(async () => probe("UP")));

    render(<SkillforgeHome session={null} />);

    expect(screen.getByRole("link", { name: "Entrar" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Sair" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Catálogo" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Turmas" })).not.toBeInTheDocument();
  });

  it.each([
    ["aluno", "Aluno"],
    ["docente", "Docente"],
    ["administracao", "Administração"],
  ])("shows the signed-in %s", (role, label) => {
    vi.stubGlobal("fetch", vi.fn(async () => probe("UP")));

    render(<SkillforgeHome session={{ name: "Conta", role, label }} />);

    const access = screen.getByRole("region", { name: "Acesso" });
    expect(within(access).getByText("Conta")).toBeInTheDocument();
    expect(within(access).getByText(label)).toBeInTheDocument();
    expect(within(access).getByRole("link", { name: "Sair" })).toBeInTheDocument();
    expect(within(access).getByRole("link", { name: "Catálogo" })).toBeInTheDocument();
    expect(within(access).getByRole("link", { name: "Turmas" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Entrar" })).not.toBeInTheDocument();
  });
});
