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
          <option value="scorm">Pacote SCORM</option>
          <option value="lti">Ferramenta externa</option>
        </select>
      </label>
      {kind === "markdown" ? (
        <label>
          Texto
          <textarea name="markdown" required />
        </label>
      ) : null}
      {kind === "lti" ? (
        <>
          <label>
            Endereço de abertura
            <input name="launchUrl" type="url" required placeholder="https://" />
          </label>
          <label>
            Segredo compartilhado
            <input name="secret" required minLength={8} autoComplete="off" />
          </label>
          <label>
            Login da ferramenta
            <input name="authLogin" type="url" placeholder="https://tool.example/login" />
          </label>
        </>
      ) : null}
      {kind !== "markdown" && kind !== "lti" ? (
        <label>
          Arquivo
          <input name="file" type="file" required accept={accept(kind)} />
        </label>
      ) : null}
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
    case "scorm":
      return ".zip,application/zip";
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
    case "scorm":
      return "Zip com imsmanifest.xml, até 100 MB.";
    case "lti":
      return "A turma abre a ferramenta com a conta de quem está na aula.";
    default:
      return "Texto da página, como uma aula escrita.";
  }
}
