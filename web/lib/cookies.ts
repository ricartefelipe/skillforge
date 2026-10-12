export const STATE_COOKIE = "skillforge_state";
export const VERIFIER_COOKIE = "skillforge_verifier";
export const ACCESS_COOKIE = "skillforge_access";
export const ID_COOKIE = "skillforge_id";

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
