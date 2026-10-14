import { apiUrl } from "./openid";

export async function readJson(token: string, path: string): Promise<{ ok: boolean; body: unknown } | null> {
  try {
    const response = await fetch(`${apiUrl()}${path}`, {
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return { ok: response.ok, body };
  } catch {
    return null;
  }
}

export async function readIds(token: string, path: string): Promise<string[]> {
  const read = await readJson(token, path);
  if (read === null || !read.ok || !Array.isArray(read.body)) {
    return [];
  }
  return read.body.filter((item): item is string => typeof item === "string");
}
