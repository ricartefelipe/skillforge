import { describe, expect, it, vi } from "vitest";

import { loadSession, roleLabel, sessionFromMe } from "./access";

describe("roleLabel", () => {
  it("names the three institutional roles", () => {
    expect(roleLabel("aluno")).toBe("Aluno");
    expect(roleLabel("docente")).toBe("Docente");
    expect(roleLabel("administracao")).toBe("Administração");
  });

  it("does not invent a label for an unknown role", () => {
    expect(roleLabel("offline_access")).toBeNull();
  });
});

describe("sessionFromMe", () => {
  it("accepts a confirmed account", () => {
    expect(sessionFromMe(true, { name: "Aluno", role: "aluno" })).toEqual({
      name: "Aluno",
      role: "aluno",
      label: "Aluno",
    });
  });

  it("refuses a failure, a blank name, or an unknown role", () => {
    expect(sessionFromMe(false, { name: "Aluno", role: "aluno" })).toBeNull();
    expect(sessionFromMe(true, { name: "  ", role: "aluno" })).toBeNull();
    expect(sessionFromMe(true, { name: "Aluno", role: "visitante" })).toBeNull();
    expect(sessionFromMe(true, null)).toBeNull();
  });
});

describe("loadSession", () => {
  it("returns the account when the API accepts the token", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        json: async () => ({ name: "Docente", role: "docente" }),
      })),
    );

    await expect(loadSession("token", "http://localhost:8080")).resolves.toEqual({
      name: "Docente",
      role: "docente",
      label: "Docente",
    });
    vi.unstubAllGlobals();
  });

  it("does not show an account when the API cannot be read", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("connection refused");
      }),
    );

    await expect(loadSession("token", "http://localhost:8080")).resolves.toBeNull();
    vi.unstubAllGlobals();
  });
});
