import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE } from "../../../../../lib/cookies";
import { apiUrl, webUrl } from "../../../../../lib/openid";

export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    return NextResponse.redirect(new URL("/login", webUrl()));
  }

  let response: Response;
  try {
    response = await fetch(`${apiUrl()}/lessons/${id}/media`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
  } catch {
    return new NextResponse(null, { status: 503 });
  }
  if (!response.ok) {
    return new NextResponse(null, { status: response.status });
  }

  const bytes = await response.arrayBuffer();
  const type = response.headers.get("content-type") ?? "application/octet-stream";
  return new NextResponse(bytes, {
    status: 200,
    headers: {
      "content-type": type,
      "cache-control": "private, no-store",
    },
  });
}
