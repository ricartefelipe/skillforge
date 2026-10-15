import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE, ID_COOKIE, sessionCookie } from "../../lib/cookies";
import { clientId, issuer, webUrl } from "../../lib/openid";

export function GET() {
  return endSession();
}

export function POST() {
  return endSession();
}

async function endSession() {
  const jar = await cookies();
  const idToken = jar.get(ID_COOKIE)?.value;
  const response = new NextResponse(logoutPage(idToken), {
    headers: { "content-type": "text/html; charset=utf-8" },
  });
  const cleared = { ...sessionCookie(0), expires: new Date(0) };
  response.cookies.set(ACCESS_COOKIE, "", cleared);
  response.cookies.set(ID_COOKIE, "", cleared);
  return response;
}

function logoutPage(idToken: string | undefined): string {
  const hint = idToken ? `<input type="hidden" name="id_token_hint" value="${escapeAttr(idToken)}"/>` : "";
  return `<!doctype html>
<html lang="pt-BR">
  <body>
    <p>Saindo da oficina.</p>
    <form id="out" method="post" action="${escapeAttr(`${issuer()}/protocol/openid-connect/logout`)}">
      <input type="hidden" name="client_id" value="${escapeAttr(clientId())}"/>
      <input type="hidden" name="post_logout_redirect_uri" value="${escapeAttr(`${webUrl()}/`)}"/>
      ${hint}
      <button type="submit">Sair</button>
    </form>
    <script>document.getElementById("out").submit()</script>
  </body>
</html>`;
}

function escapeAttr(value: string): string {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;");
}
