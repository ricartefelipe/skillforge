import { describe, expect, it } from "vitest";

import { gaugeLabel, headline, readingFromProbe } from "./probes";

describe("readingFromProbe", () => {
  it("counts only an HTTP success whose status is UP", () => {
    expect(readingFromProbe(true, { status: "UP" })).toBe("up");
  });

  it("refuses a body that says UP when the HTTP status is a failure", () => {
    expect(readingFromProbe(false, { status: "UP" })).toBe("down");
  });

  it("refuses HTTP success when the probe status is not UP", () => {
    expect(readingFromProbe(true, { status: "DOWN" })).toBe("down");
    expect(readingFromProbe(true, { status: "OUT_OF_SERVICE" })).toBe("down");
  });

  it("refuses a body that is not a probe", () => {
    expect(readingFromProbe(true, null)).toBe("down");
    expect(readingFromProbe(true, "UP")).toBe("down");
    expect(readingFromProbe(true, {})).toBe("down");
    expect(readingFromProbe(true, { status: 1 })).toBe("down");
  });
});

describe("headline", () => {
  it("waits until both probes have been read", () => {
    expect(headline("pending", "pending")).toBe("Consultando o ambiente.");
    expect(headline("up", "pending")).toBe("Consultando o ambiente.");
  });

  it("says the environment is up only when both probes are up", () => {
    expect(headline("up", "up")).toBe("O ambiente está de pé.");
  });

  it("keeps the process visible when readiness refuses traffic", () => {
    expect(headline("up", "down")).toBe("O processo vive. Ainda não atende.");
  });

  it("does not claim the process is alive when liveness fails", () => {
    expect(headline("down", "up")).toBe("O ambiente não responde.");
    expect(headline("down", "down")).toBe("O ambiente não responde.");
  });
});

describe("gaugeLabel", () => {
  it("uses the words of each gauge", () => {
    expect(gaugeLabel("pending", "no ar", "fora do ar")).toBe("consultando");
    expect(gaugeLabel("up", "no ar", "fora do ar")).toBe("no ar");
    expect(gaugeLabel("down", "pronta", "indisponível")).toBe("indisponível");
  });
});
