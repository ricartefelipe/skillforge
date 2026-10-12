import { createHash, randomBytes } from "node:crypto";

export type LoginRequest = {
  state: string;
  verifier: string;
  challenge: string;
};

export type Tokens = {
  accessToken: string;
  idToken: string;
  expiresIn: number;
};

export class LoginRejected extends Error {
  constructor(reason: "state" | "token") {
    super(reason);
    this.name = "LoginRejected";
  }
}

export function createLoginRequest(): LoginRequest {
  const state = randomBytes(24).toString("base64url");
  const verifier = randomBytes(32).toString("base64url");
  const challenge = createHash("sha256").update(verifier).digest("base64url");
  return { state, verifier, challenge };
}

export function authorizeUrl(input: {
  issuer: string;
  clientId: string;
  redirectUri: string;
  request: LoginRequest;
}): string {
  const url = new URL(`${input.issuer}/protocol/openid-connect/auth`);
  url.searchParams.set("client_id", input.clientId);
  url.searchParams.set("redirect_uri", input.redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid");
  url.searchParams.set("state", input.request.state);
  url.searchParams.set("code_challenge", input.request.challenge);
  url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}

export async function exchangeCode(input: {
  issuer: string;
  clientId: string;
  redirectUri: string;
  code: string;
  verifier: string;
  expectedState: string;
  actualState: string | null;
  fetchToken: (url: string, body: URLSearchParams) => Promise<{ ok: boolean; body: unknown }>;
}): Promise<Tokens> {
  if (input.actualState === null || input.actualState !== input.expectedState) {
    throw new LoginRejected("state");
  }

  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: input.clientId,
    redirect_uri: input.redirectUri,
    code: input.code,
    code_verifier: input.verifier,
  });
  const response = await input.fetchToken(`${input.issuer}/protocol/openid-connect/token`, body);
  if (!response.ok || response.body === null || typeof response.body !== "object") {
    throw new LoginRejected("token");
  }

  const accessToken = "access_token" in response.body ? response.body.access_token : undefined;
  const idToken = "id_token" in response.body ? response.body.id_token : undefined;
  const expiresIn = "expires_in" in response.body ? response.body.expires_in : undefined;
  if (typeof accessToken !== "string" || typeof idToken !== "string" || typeof expiresIn !== "number") {
    throw new LoginRejected("token");
  }

  return { accessToken, idToken, expiresIn };
}
