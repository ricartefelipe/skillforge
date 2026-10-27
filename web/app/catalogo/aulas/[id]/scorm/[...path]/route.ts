import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE } from "../../../../../../lib/cookies";
import { apiUrl, webUrl } from "../../../../../../lib/openid";
import { injectScormBridge, isHtml } from "../../../../../../lib/scorm";

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
  // The page runs inside a sandboxed frame, so its own LMS runtime is handed to it here.
  if (isHtml(type)) {
    const html = injectScormBridge(await response.text());
    return new NextResponse(html, {
      status: 200,
      headers: { "content-type": type, "cache-control": "private, no-store" },
    });
  }
  const bytes = await response.arrayBuffer();
  return new NextResponse(bytes, {
    status: 200,
    headers: { "content-type": type, "cache-control": "private, no-store" },
  });
}
