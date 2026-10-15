"use client";

import { useState } from "react";

export function LessonForm({
  moduleId,
  action,
  returnTo = "/catalogo",
}: {
  moduleId: string;
  action: (formData: FormData) => void;
  returnTo?: string;
}) {
  const [kind, setKind] = useState("markdown");

  return (
    <form className="compose" action={action} aria-label="Nova aula">
      <input type="hidden" name="moduleId" value={moduleId} />
      <input type="hidden" name="returnTo" value={returnTo} />
      <label>
        Título
        <input name="title" required />
      </label>
      <label>
        Tipo
        <select name="kind" value={kind} onChange={(event) => setKind(event.target.value)}>
          <option value="markdown">Markdown</option>
          <option value="pdf">PDF</option>
          <option value="video">Vídeo</option>
        </select>
      </label>
      {kind === "markdown" ? (
        <label>
          Texto
          <textarea name="markdown" required />
        </label>
      ) : (
        <label>
          Arquivo
          <input
            name="file"
            type="file"
            required
            accept={kind === "pdf" ? "application/pdf" : "video/mp4,video/webm"}
          />
        </label>
      )}
      <button type="submit">Publicar aula</button>
    </form>
  );
}
