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
