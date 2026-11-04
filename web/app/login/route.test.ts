// @vitest-environment node
import { createHash } from "node:crypto";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const jar = new Map<string, string>();

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (jar.has(name) ? { name, value: jar.get(name) as string } : undefined),
    getAll: () => [...jar.entries()].map(([name, value]) => ({ name, value })),
  }),
}));

const { GET: login } = await import("./route");
const { GET: callback } = await import("../callback/route");

// Applies the Set-Cookie headers of a response to the browser jar, in the order the browser receives them.
function store(response: Response) {
  for (const header of response.headers.getSetCookie()) {
    const [pair, ...attributes] = header.split(";");
    const separator = pair.indexOf("=");
    const name = pair.slice(0, separator).trim();
    const value = pair.slice(separator + 1).trim();
    const expired = attributes.some((attribute) => /^\s*max-age=0\s*$/i.test(attribute))
      || attributes.some((attribute) => /^\s*expires=thu, 01 jan 1970/i.test(attribute));
    if (expired || value === "") {
      jar.delete(name);
    } else {
      jar.set(name, value);
    }
  }
}

function stateOf(response: Response): string {
  return new URL(response.headers.get("location") as string).searchParams.get("state") as string;
}

function challengeOf(response: Response): string {
  return new URL(response.headers.get("location") as string).searchParams.get("code_challenge") as string;
}

function loginRequest(headers: Record<string, string> = {}) {
  return new Request("http://localhost:3000/login", { headers });
}

describe("login", () => {
  let challenges: Map<string, string>;

  beforeEach(() => {
    jar.clear();
    challenges = new Map();
    // The provider only issues tokens when the verifier matches the challenge of the code's authorization request.
    vi.stubGlobal("fetch", vi.fn(async (_url: string, init: { body: URLSearchParams }) => {
      const code = init.body.get("code") as string;
      const verifier = init.body.get("code_verifier") as string;
      const challenge = createHash("sha256").update(verifier).digest("base64url");
      if (challenges.get(code) !== challenge) {
        return new Response(JSON.stringify({ error: "invalid_grant" }), { status: 400 });
      }
      return new Response(JSON.stringify({ access_token: "access", id_token: "id", expires_in: 300 }), { status: 200 });
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("signs in when a second login answers before the callback, as a prefetch racing the navigation does", async () => {
    // Both requests leave the browser before either answer arrives, so neither sees the other's cookies.
    const navigation = login(loginRequest());
    const prefetch = login(loginRequest());
    challenges.set("code-from-navigation", challengeOf(navigation));
    store(navigation);
    store(prefetch);

    store(await callback(new Request(`http://localhost:3000/callback?code=code-from-navigation&state=${stateOf(navigation)}`)));

    expect(jar.get("skillforge_access")).toBe("access");
  });

  it("does not open a pending login for a router prefetch", async () => {
    const response = login(loginRequest({ "next-router-prefetch": "1" }));

    expect(response.status).toBe(204);
    expect(response.headers.getSetCookie()).toEqual([]);
  });

  it("rejects a callback whose state this browser never asked for", async () => {
    const navigation = login(loginRequest());
    challenges.set("code", challengeOf(navigation));
    store(navigation);

    store(await callback(new Request("http://localhost:3000/callback?code=code&state=forged")));

    expect(jar.get("skillforge_access")).toBeUndefined();
  });

  it("forgets every pending login once one of them completes", async () => {
    const first = login(loginRequest());
    const second = login(loginRequest());
    challenges.set("code", challengeOf(second));
    store(first);
    store(second);

    store(await callback(new Request(`http://localhost:3000/callback?code=code&state=${stateOf(second)}`)));

    expect([...jar.keys()].filter((name) => name.startsWith("skillforge_login_"))).toEqual([]);
    expect(jar.get("skillforge_access")).toBe("access");
  });
});
