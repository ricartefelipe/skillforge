import { NextResponse } from "next/server";

import { ACCESS_COOKIE, ID_COOKIE, isPrefetch, loginCookieName, sessionCookie } from "../../lib/cookies";
import { authorizeUrl, createLoginRequest } from "../../lib/login";
import { clientId, issuer, redirectUri } from "../../lib/openid";

export function GET(request: Request) {
  if (isPrefetch(request)) {
    return new NextResponse(null, { status: 204, headers: { "cache-control": "no-store" } });
  }
  const login = createLoginRequest();
  const response = NextResponse.redirect(authorizeUrl({
    issuer: issuer(),
    clientId: clientId(),
    redirectUri: redirectUri(),
    request: login,
  }));
  response.headers.set("cache-control", "no-store");
  response.cookies.set(loginCookieName(login.state) as string, login.verifier, sessionCookie(300));
  response.cookies.set(ACCESS_COOKIE, "", sessionCookie(0));
  response.cookies.set(ID_COOKIE, "", sessionCookie(0));
  return response;
}
