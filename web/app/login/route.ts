import { NextResponse } from "next/server";

import { ACCESS_COOKIE, ID_COOKIE, sessionCookie, STATE_COOKIE, VERIFIER_COOKIE } from "../../lib/cookies";
import { authorizeUrl, createLoginRequest } from "../../lib/login";
import { clientId, issuer, redirectUri } from "../../lib/openid";

export function GET() {
  const request = createLoginRequest();
  const response = NextResponse.redirect(authorizeUrl({
    issuer: issuer(),
    clientId: clientId(),
    redirectUri: redirectUri(),
    request,
  }));
  const pending = sessionCookie(300);
  response.cookies.set(STATE_COOKIE, request.state, pending);
  response.cookies.set(VERIFIER_COOKIE, request.verifier, pending);
  response.cookies.set(ACCESS_COOKIE, "", sessionCookie(0));
  response.cookies.set(ID_COOKIE, "", sessionCookie(0));
  return response;
}
