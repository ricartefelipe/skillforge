export const STATE_COOKIE = "skillforge_state";
export const VERIFIER_COOKIE = "skillforge_verifier";
export const ACCESS_COOKIE = "skillforge_access";
export const ID_COOKIE = "skillforge_id";
// Each pending login keeps its own PKCE verifier under a cookie named after its state, so two logins started before
// the callback (a router prefetch racing the navigation, a double click, two tabs) do not overwrite each other.
export const LOGIN_COOKIE_PREFIX = "skillforge_login_";
const STATE_SHAPE = /^[A-Za-z0-9_-]{16,64}$/;

export function loginCookieName(state: string): string | null {
  return STATE_SHAPE.test(state) ? `${LOGIN_COOKIE_PREFIX}${state}` : null;
}

// Router and browser prefetches must not run handlers that change the session.
export function isPrefetch(request: Request): boolean {
  const purpose = `${request.headers.get("sec-purpose") ?? ""} ${request.headers.get("purpose") ?? ""}`;
  return request.headers.has("next-router-prefetch")
    || request.headers.has("next-router-segment-prefetch")
    || /prefetch|prerender/i.test(purpose);
}

export function sessionCookie(maxAge: number) {
  return {
    httpOnly: true,
    sameSite: "lax" as const,
    // The local stack is served over http, so the browser would drop a secure cookie.
    secure: false,
    path: "/",
    maxAge,
  };
}
