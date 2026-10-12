import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE, ID_COOKIE, sessionCookie } from "../../lib/cookies";
import { clientId, issuer, webUrl } from "../../lib/openid";

export async function GET() {
  const jar = await cookies();
  const idToken = jar.get(ID_COOKIE)?.value;
  const destination = idToken ? logoutUrl(idToken) : new URL("/", webUrl());
  const response = NextResponse.redirect(destination);
  response.cookies.set(ACCESS_COOKIE, "", sessionCookie(0));
  response.cookies.set(ID_COOKIE, "", sessionCookie(0));
  return response;
}

function logoutUrl(idToken: string): URL {
  const url = new URL(`${issuer()}/protocol/openid-connect/logout`);
  url.searchParams.set("id_token_hint", idToken);
  url.searchParams.set("client_id", clientId());
  url.searchParams.set("post_logout_redirect_uri", `${webUrl()}/`);
  return url;
}
