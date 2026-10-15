import { act } from "react";
import { hydrateRoot } from "react-dom/client";
import { renderToString } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/font/google", () => ({
  Newsreader: () => ({ variable: "display" }),
  Literata: () => ({ variable: "literata" }),
  Sora: () => ({ variable: "grotesk" }),
}));

import RootLayout from "./layout";

afterEach(() => {
  localStorage.clear();
});

describe("RootLayout", () => {
  it("hydrates when the night theme was written before React", async () => {
    localStorage.setItem("skillforge-theme", "night");
    const markup = renderToString(
      <RootLayout>
        <p>oficina</p>
      </RootLayout>,
    );

    document.open();
    document.write(`<!DOCTYPE html>${markup}`);
    document.close();

    const mismatches: string[] = [];
    const report = vi.spyOn(console, "error").mockImplementation((...args: unknown[]) => {
      mismatches.push(args.map(String).join(" "));
    });
    await act(async () => {
      hydrateRoot(
        document,
        <RootLayout>
          <p>oficina</p>
        </RootLayout>,
      );
    });
    report.mockRestore();

    expect(document.documentElement.dataset.theme).toBe("night");
    expect(mismatches.join("\n")).not.toMatch(/didn't match|data-theme/);
  });
});
