import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { ACCESS_COOKIE } from "../../../../../lib/cookies";
import { apiUrl } from "../../../../../lib/openid";

export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    return NextResponse.json({}, { status: 401 });
  }
  const response = await fetch(`${apiUrl()}/lessons/${id}/launch`, {
    headers: { authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!response.ok) {
    return NextResponse.json({}, { status: response.status });
  }
  return NextResponse.json(await response.json());
}
