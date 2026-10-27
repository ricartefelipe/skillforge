import { describe, expect, it } from "vitest";

import { passThrough, rangeHeaders } from "./media";

describe("rangeHeaders", () => {
  it("forwards the range when the caller asks for one", () => {
    const request = new Request("http://x/a", { headers: { range: "bytes=2-5" } });

    expect(rangeHeaders(request, "t")).toEqual({ authorization: "Bearer t", range: "bytes=2-5" });
  });

  it("omits the range when there is none", () => {
    expect(rangeHeaders(new Request("http://x/a"), "t")).toEqual({ authorization: "Bearer t" });
  });
});

describe("passThrough", () => {
  it("keeps the partial status and the headers a player needs", async () => {
    const upstream = new Response("ello", {
      status: 206,
      headers: { "content-type": "video/mp4", "content-range": "bytes 2-5/8", "accept-ranges": "bytes" },
    });

    const out = passThrough(upstream);

    expect(out.status).toBe(206);
    expect(out.headers.get("content-range")).toBe("bytes 2-5/8");
    expect(out.headers.get("accept-ranges")).toBe("bytes");
    expect(out.headers.get("cache-control")).toBe("private, no-store");
    expect(await out.text()).toBe("ello");
  });

  it("drops the body and keeps the status when the upstream failed", async () => {
    const out = passThrough(new Response("no", { status: 404 }));

    expect(out.status).toBe(404);
    expect(await out.text()).toBe("");
  });
});
