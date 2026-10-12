import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE, ID_COOKIE, sessionCookie, STATE_COOKIE, VERIFIER_COOKIE } from "../../lib/cookies";
import { exchangeCode } from "../../lib/login";
import { clientId, issuer, redirectUri, webUrl } from "../../lib/openid";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const jar = await cookies();
  const home = NextResponse.redirect(new URL("/", webUrl()));
  home.cookies.set(STATE_COOKIE, "", sessionCookie(0));
  home.cookies.set(VERIFIER_COOKIE, "", sessionCookie(0));

  const code = url.searchParams.get("code");
  if (url.searchParams.get("error") || code === null) {
    return home;
  }

  try {
    const tokens = await exchangeCode({
      issuer: issuer(),
      clientId: clientId(),
      redirectUri: redirectUri(),
      code,
      verifier: jar.get(VERIFIER_COOKIE)?.value ?? "",
      expectedState: jar.get(STATE_COOKIE)?.value ?? "",
      actualState: url.searchParams.get("state"),
      fetchToken: async (tokenUrl, body) => {
        const response = await fetch(tokenUrl, {
          method: "POST",
          headers: { "content-type": "application/x-www-form-urlencoded" },
          body,
        });
        const payload: unknown = await response.json();
        return { ok: response.ok, body: payload };
      },
    });
    home.cookies.set(ACCESS_COOKIE, tokens.accessToken, sessionCookie(tokens.expiresIn));
    home.cookies.set(ID_COOKIE, tokens.idToken, sessionCookie(tokens.expiresIn));
    return home;
  } catch {
    return home;
  }
}
