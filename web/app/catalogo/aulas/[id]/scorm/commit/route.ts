import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE } from "../../../../../../lib/cookies";
import { apiUrl } from "../../../../../../lib/openid";

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    return new NextResponse(null, { status: 401 });
  }
  const response = await fetch(`${apiUrl()}/lessons/${id}/scorm/commit`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: await request.text(),
    cache: "no-store",
  });
  return new NextResponse(null, { status: response.status });
}
