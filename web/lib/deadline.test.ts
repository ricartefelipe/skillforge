import { describe, expect, it } from "vitest";

import { kindLabel, nextInTrail, type Course } from "./catalog";
import { deadlineDay, formatDeadline } from "./deadline";

const course: Course = {
  id: "c1",
  name: "Oficina",
  modules: [
    {
      id: "m1",
      name: "Videoaulas",
      lessons: [
        { id: "a", title: "O que é uma competência", kind: "video" },
        { id: "b", title: "A prática deliberada em vídeo", kind: "video" },
      ],
    },
    {
      id: "m2",
      name: "Oficina",
      lessons: [{ id: "c", title: "A primeira peça", kind: "markdown" }],
    },
  ],
};

describe("kindLabel", () => {
  it("names a pdf apart from an ebook", () => {
    expect(kindLabel("pdf")).toBe("PDF");
    expect(kindLabel("ebook")).toBe("Ebook");
    expect(kindLabel("video")).toBe("Vídeo");
  });
});

describe("nextInTrail", () => {
  it("walks into the next module", () => {
    expect(nextInTrail(course, "b")?.title).toBe("A primeira peça");
    expect(nextInTrail(course, "c")).toBeNull();
  });
});

describe("formatDeadline", () => {
  it("reads an instant in the São Paulo clock", () => {
    expect(formatDeadline("2026-10-20T23:00:00Z")).toBe("20 de outubro, 20h");
    expect(formatDeadline("2026-10-20T23:30:00Z")).toBe("20 de outubro, 20h30");
    expect(deadlineDay("2026-10-21T01:00:00Z")).toBe("2026-10-20");
  });
});
