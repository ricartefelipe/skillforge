import { describe, expect, it } from "vitest";

import { doubtsFromBody } from "./forum";
import { reportsFromBody } from "./report";

describe("doubtsFromBody", () => {
  it("reads a doubt with its replies and rejects a broken one", () => {
    const body = [
      {
        id: "d1",
        author: "aluno",
        title: "Dúvida",
        body: "Texto",
        replies: [{ id: "r1", author: "docente", body: "Resposta" }],
      },
    ];

    expect(doubtsFromBody(true, body)).toEqual(body);
    expect(doubtsFromBody(false, body)).toBeNull();
    expect(doubtsFromBody(true, [{ id: "d1", author: "aluno", title: "Dúvida", body: "Texto" }])).toBeNull();
  });
});

describe("reportsFromBody", () => {
  it("keeps the average from the server and hides a certificate that was not earned", () => {
    const report = {
      cohortId: "t1",
      cohortName: "Manhã",
      courseName: "Forja",
      practiceWeight: 1,
      examWeight: 1,
      practices: [],
      exams: [],
      average: null,
      certified: false,
    };

    expect(reportsFromBody(true, [report])?.[0].average).toBeNull();
    expect(reportsFromBody(true, [{ ...report, average: "9.0", certified: true }])?.[0].certified).toBe(true);
    expect(reportsFromBody(true, [{ cohortId: "t1" }])).toBeNull();
  });
});
