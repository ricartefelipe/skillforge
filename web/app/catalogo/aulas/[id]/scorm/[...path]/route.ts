import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE } from "../../../../../../lib/cookies";
import { apiUrl, webUrl } from "../../../../../../lib/openid";
import { injectScormBridge, isHtml } from "../../../../../../lib/scorm";
import { issueScormTicket, scormFileBase } from "../../../../../../lib/scorm-ticket";

export async function GET(_request: Request, context: { params: Promise<{ id: string; path: string[] }> }) {
  const { id, path } = await context.params;
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    return NextResponse.redirect(new URL("/login", webUrl()));
  }
  const response = await fetch(`${apiUrl()}/lessons/${id}/scorm/file?path=${encodeURIComponent(path.join("/"))}`, {
    headers: { authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!response.ok) {
    return new NextResponse(null, { status: response.status });
  }
  const type = response.headers.get("content-type") ?? "application/octet-stream";
  // The page runs inside a sandboxed frame, so its own LMS runtime is handed to it here, together
  // with the base that lets its relative addresses reach the files.
  if (isHtml(type)) {
    const ticket = await issueScormTicket(id, token);
    if (ticket === null) {
      return new NextResponse(null, { status: 403 });
    }
    return new NextResponse(injectScormBridge(await response.text(), scormFileBase(id, ticket)), {
      status: 200,
      headers: { "content-type": type, "cache-control": "private, no-store", "referrer-policy": "no-referrer" },
    });
  }
  return new NextResponse(await response.arrayBuffer(), {
    status: 200,
    headers: { "content-type": type, "cache-control": "private, no-store" },
  });
}
