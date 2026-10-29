import { afterEach, describe, expect, it, vi } from "vitest";

import { issueScormTicket, scormFileBase, scormFileUrl } from "./scorm-ticket";

describe("issueScormTicket", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("pede o bilhete à API com a sessão e devolve o token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ticket: "t-1" }), { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);

    expect(await issueScormTicket("aula-1", "sessao")).toBe("t-1");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/lessons\/aula-1\/scorm\/ticket$/);
    expect(init.method).toBe("POST");
    expect((init.headers as Record<string, string>).authorization).toBe("Bearer sessao");
  });

  it("devolve nulo quando a API recusa", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 404 })));

    expect(await issueScormTicket("aula-1", "sessao")).toBeNull();
  });
});

describe("endereços do bilhete", () => {
  it("a base aponta para o caminho dos arquivos daquela aula, com o bilhete", () => {
    expect(scormFileBase("aula-1", "t-1")).toBe("/catalogo/aulas/aula-1/scorm-arquivo/t-1/");
  });

  it("o arquivo é lido na API pelo bilhete, sem sessão, com o caminho codificado", () => {
    expect(scormFileUrl("aula-1", "t-1", "assets/a b.js")).toMatch(
      /\/lessons\/aula-1\/scorm\/files\/t-1\?path=assets%2Fa%20b\.js$/,
    );
  });
});
