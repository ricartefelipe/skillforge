import { describe, expect, it, vi } from "vitest";

import { authorizeUrl, createLoginRequest, exchangeCode, LoginRejected } from "./login";

const issuer = "http://localhost:8180/realms/skillforge";

describe("createLoginRequest", () => {
  it("creates a challenge from the verifier", () => {
    const request = createLoginRequest();

    expect(request.state).not.toBe(request.verifier);
    expect(request.challenge.length).toBeGreaterThan(20);
    expect(authorizeUrl({
      issuer,
      clientId: "skillforge",
      redirectUri: "http://localhost:3000/callback",
      request,
    })).toContain(`code_challenge=${request.challenge}`);
  });
});

describe("exchangeCode", () => {
  it("does not exchange the code when the state does not match", async () => {
    const fetchToken = vi.fn();

    await expect(exchangeCode({
      issuer,
      clientId: "skillforge",
      redirectUri: "http://localhost:3000/callback",
      code: "code",
      verifier: "verifier",
      expectedState: "expected",
      actualState: "other",
      fetchToken,
    })).rejects.toBeInstanceOf(LoginRejected);
    expect(fetchToken).not.toHaveBeenCalled();
  });

  it("returns the tokens when the provider accepts the code", async () => {
    const fetchToken = vi.fn(async () => ({
      ok: true,
      body: { access_token: "access", id_token: "id", expires_in: 300 },
    }));

    await expect(exchangeCode({
      issuer,
      clientId: "skillforge",
      redirectUri: "http://localhost:3000/callback",
      code: "code",
      verifier: "verifier",
      expectedState: "same",
      actualState: "same",
      fetchToken,
    })).resolves.toEqual({ accessToken: "access", idToken: "id", expiresIn: 300 });
  });

  it("rejects a token response that is not a session", async () => {
    const fetchToken = vi.fn(async () => ({ ok: false, body: {} }));

    await expect(exchangeCode({
      issuer,
      clientId: "skillforge",
      redirectUri: "http://localhost:3000/callback",
      code: "code",
      verifier: "verifier",
      expectedState: "same",
      actualState: "same",
      fetchToken,
    })).rejects.toBeInstanceOf(LoginRejected);
  });
});
