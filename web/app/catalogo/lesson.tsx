"use client";

import ReactMarkdown from "react-markdown";

import type { LessonDetail } from "../../lib/catalog";
import { ScormPlayer } from "./scorm-player";
import { ToolLaunch } from "./tool-launch";

export function LessonView({ lesson }: { lesson: LessonDetail }) {
  const media = `/catalogo/aulas/${lesson.id}/arquivo`;

  return (
    <article className="lesson-body">
      {lesson.kind === "markdown" ? <ReactMarkdown>{lesson.markdown ?? ""}</ReactMarkdown> : null}
      {lesson.kind === "pdf" ? (
        <>
          <a className="file-link" href={media}>Abrir o ebook</a>
          <iframe title="Ebook da aula" src={media} />
        </>
      ) : null}
      {lesson.kind === "ebook" ? <a className="file-link" href={media}>Baixar o ebook</a> : null}
      {lesson.kind === "video" ? <video controls src={media} aria-label="Vídeo da aula" /> : null}
      {lesson.kind === "scorm" ? <ScormPlayer lessonId={lesson.id} /> : null}
      {lesson.kind === "lti" ? <ToolLaunch lessonId={lesson.id} /> : null}
    </article>
  );
}
