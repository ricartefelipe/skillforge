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
    <form className="compose" action={action} aria-label="Novo material">
      <input type="hidden" name="moduleId" value={moduleId} />
      <input type="hidden" name="returnTo" value={returnTo} />
      <label>
        Título
        <input name="title" required />
      </label>
      <label>
        Material
        <select name="kind" value={kind} onChange={(event) => setKind(event.target.value)}>
          <option value="markdown">Página</option>
          <option value="ebook">Ebook (PDF ou EPUB)</option>
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
          <input name="file" type="file" required accept={accept(kind)} />
        </label>
      )}
      <p className="meta">{hint(kind)}</p>
      <button type="submit">Publicar material</button>
    </form>
  );
}

function accept(kind: string): string {
  switch (kind) {
    case "ebook":
      return ".pdf,.epub,application/pdf,application/epub+zip";
    case "video":
      return ".mp4,.mov,.webm,video/mp4,video/quicktime,video/webm";
    default:
      return "";
  }
}

function hint(kind: string): string {
  switch (kind) {
    case "ebook":
      return "PDF ou EPUB, até 100 MB.";
    case "video":
      return "MP4, MOV ou WebM, até 512 MB.";
    default:
      return "Texto da página, como uma aula escrita.";
  }
}
