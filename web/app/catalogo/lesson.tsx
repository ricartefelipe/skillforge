"use client";

import ReactMarkdown from "react-markdown";

import type { LessonDetail } from "../../lib/catalog";

export function LessonView({ lesson }: { lesson: LessonDetail }) {
  const media = `/catalogo/aulas/${lesson.id}/arquivo`;

  return (
    <article className="lesson-body">
      {lesson.kind === "markdown" ? <ReactMarkdown>{lesson.markdown ?? ""}</ReactMarkdown> : null}
      {lesson.kind === "pdf" ? (
        <>
          <a href={media}>Abrir o PDF</a>
          <iframe title="PDF da aula" src={media} />
        </>
      ) : null}
      {lesson.kind === "video" ? <video controls src={media} aria-label="Vídeo da aula" /> : null}
    </article>
  );
}
