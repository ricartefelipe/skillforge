import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE } from "../../../../../lib/cookies";
import { apiUrl, webUrl } from "../../../../../lib/openid";
import { passThrough, rangeHeaders } from "../../../../../lib/media";

export async function GET(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    return NextResponse.redirect(new URL("/login", webUrl()));
  }

  let response: Response;
  try {
    response = await fetch(`${apiUrl()}/lessons/${id}/media`, {
      headers: rangeHeaders(request, token),
      cache: "no-store",
    });
  } catch {
    return new NextResponse(null, { status: 503 });
  }
  return passThrough(response);
}
