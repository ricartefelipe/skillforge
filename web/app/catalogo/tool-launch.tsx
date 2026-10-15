"use client";

import { useEffect, useState } from "react";

export function ToolLaunch({ lessonId }: { lessonId: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [fields, setFields] = useState<[string, string][]>([]);

  useEffect(() => {
    void fetch(`/catalogo/aulas/${lessonId}/launch`)
      .then((response) => response.json())
      .then((body: unknown) => {
        if (body === null || typeof body !== "object" || !("url" in body) || typeof body.url !== "string") {
          return;
        }
        const record = body as { url: string; fields?: Record<string, string> };
        const entries = Object.entries(record.fields ?? {}).filter(
          (entry): entry is [string, string] => typeof entry[1] === "string",
        );
        setUrl(record.url);
        setFields(entries);
      })
      .catch(() => setUrl(null));
  }, [lessonId]);

  if (url === null) {
    return <p className="empty">A ferramenta ainda não abriu.</p>;
  }
  return (
    <form action={url} method="post" target="_blank">
      {fields.map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <button type="submit">Abrir ferramenta</button>
    </form>
  );
}
