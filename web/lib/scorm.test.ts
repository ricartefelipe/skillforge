import { describe, expect, it } from "vitest";

import { injectScormBridge, isHtml, readScormCommit, SCORM_MESSAGE_SOURCE } from "./scorm";

describe("isHtml", () => {
  it("accepts html content types and rejects the rest", () => {
    expect(isHtml("text/html; charset=utf-8")).toBe(true);
    expect(isHtml("application/zip")).toBe(false);
    expect(isHtml(null)).toBe(false);
  });
});

describe("injectScormBridge", () => {
  it("runs the bridge before the package, inside its head", () => {
    const html = injectScormBridge("<html><head><title>x</title></head><body>oi</body></html>");

    expect(html).toContain("window.API");
    const script = html.indexOf("window.API");
    expect(script).toBeLessThan(html.indexOf("<title>"));
    expect(script).toBeGreaterThan(html.indexOf("<head>"));
  });

  it("falls back to the document start when there is no head", () => {
    expect(injectScormBridge("<body>oi</body>").indexOf("window.API")).toBeLessThan(
      injectScormBridge("<body>oi</body>").indexOf("<body>"),
    );
  });
});

describe("readScormCommit", () => {
  it("reads our own message and clamps a bad score to null", () => {
    expect(readScormCommit({ source: SCORM_MESSAGE_SOURCE, status: "passed", score: 90 })).toEqual({
      status: "passed",
      score: 90,
    });
    expect(readScormCommit({ source: SCORM_MESSAGE_SOURCE, status: "completed", score: "90" })).toEqual({
      status: "completed",
      score: null,
    });
  });

  it("ignores anything that is not our message", () => {
    expect(readScormCommit({ source: "other", status: "passed", score: 1 })).toBeNull();
    expect(readScormCommit("passed")).toBeNull();
    expect(readScormCommit(null)).toBeNull();
  });
});

describe("injectScormBridge com base", () => {
  it("coloca a base antes do conteúdo, para os endereços relativos do pacote saírem por ela", () => {
    const html = injectScormBridge(
      '<html><head><link rel="stylesheet" href="assets/estilo.css"></head><body></body></html>',
      "/catalogo/aulas/aula-1/scorm-arquivo/bilhete-1/",
    );

    expect(html).toContain('<base href="/catalogo/aulas/aula-1/scorm-arquivo/bilhete-1/">');
    expect(html).toContain('<meta name="referrer" content="no-referrer">');
    expect(html.indexOf("<base")).toBeLessThan(html.indexOf("assets/estilo.css"));
  });

  it("não escreve base nenhuma quando ela não é informada", () => {
    expect(injectScormBridge("<html><head></head><body></body></html>")).not.toContain("<base");
  });
});
