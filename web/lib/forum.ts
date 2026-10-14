export type Reply = {
  id: string;
  author: string;
  body: string;
};

export type Doubt = {
  id: string;
  author: string;
  title: string;
  body: string;
  replies: Reply[];
};

export function doubtsFromBody(httpOk: boolean, body: unknown): Doubt[] | null {
  if (!httpOk || !Array.isArray(body)) {
    return null;
  }
  const doubts: Doubt[] = [];
  for (const item of body) {
    const doubt = doubtFrom(item);
    if (doubt === null) {
      return null;
    }
    doubts.push(doubt);
  }
  return doubts;
}

function doubtFrom(value: unknown): Doubt | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const id = text(value, "id");
  const author = text(value, "author");
  const title = text(value, "title");
  const body = text(value, "body");
  if (!("replies" in value) || !Array.isArray((value as Record<string, unknown>).replies)) {
    return null;
  }
  const replies: Reply[] = [];
  for (const item of (value as Record<string, unknown>).replies as unknown[]) {
    const reply = replyFrom(item);
    if (reply === null) {
      return null;
    }
    replies.push(reply);
  }
  if (id === null || author === null || title === null || body === null) {
    return null;
  }
  return { id, author, title, body, replies };
}

function replyFrom(value: unknown): Reply | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const id = text(value, "id");
  const author = text(value, "author");
  const body = text(value, "body");
  if (id === null || author === null || body === null) {
    return null;
  }
  return { id, author, body };
}

function text(value: object, key: string): string | null {
  const found = (value as Record<string, unknown>)[key];
  if (typeof found !== "string" || found.trim().length === 0) {
    return null;
  }
  return found;
}
