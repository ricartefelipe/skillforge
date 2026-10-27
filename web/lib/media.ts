// Media (video, ebook, PDF) is proxied to the API with the caller's token. The byte range the player asks
// for is forwarded, and the API answer — body and the headers a player needs — is streamed straight back,
// so a large file is never buffered whole in the proxy.

const FORWARD = ["content-type", "content-length", "content-range", "accept-ranges"];

export function rangeHeaders(request: Request, token: string): Record<string, string> {
  const headers: Record<string, string> = { authorization: `Bearer ${token}` };
  const range = request.headers.get("range");
  if (range) {
    headers.range = range;
  }
  return headers;
}

export function passThrough(response: Response): Response {
  if (!response.ok) {
    return new Response(null, { status: response.status });
  }
  const headers = new Headers({ "cache-control": "private, no-store" });
  for (const name of FORWARD) {
    const value = response.headers.get(name);
    if (value !== null) {
      headers.set(name, value);
    }
  }
  return new Response(response.body, { status: response.status, headers });
}
