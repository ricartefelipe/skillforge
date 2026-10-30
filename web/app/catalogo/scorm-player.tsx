"use client";

import { useEffect, useRef, useState } from "react";

import { readScormCommit } from "../../lib/scorm";

export function ScormPlayer({ lessonId }: { lessonId: string }) {
  const [path, setPath] = useState<string | null>(null);
  const [position, setPosition] = useState(1);
  const [steps, setSteps] = useState<{ position: number; title: string; path: string; open: boolean }[]>([]);
  const frame = useRef<HTMLIFrameElement | null>(null);
  const positionRef = useRef(position);

  useEffect(() => {
    positionRef.current = position;
  }, [position]);

  useEffect(() => {
    // The package lives in a cross-origin sandbox and can only reach us through a message it posts up.
    function onMessage(event: MessageEvent) {
      // Só o iframe do pacote (sandbox, origem opaca "null") pode falar com o player.
      if (frame.current === null || event.source !== frame.current.contentWindow || event.origin !== "null") {
        return;
      }
      const commit = readScormCommit(event.data);
      if (commit === null) {
        return;
      }
      void fetch(`/catalogo/aulas/${lessonId}/scorm/commit`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ status: commit.status, score: commit.score, position: positionRef.current }),
      });
    }
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [lessonId]);

  useEffect(() => {
    void fetch(`/catalogo/aulas/${lessonId}/scorm`)
      .then((response) => response.json())
      .then((body: unknown) => {
        if (body !== null && typeof body === "object" && "path" in body && typeof body.path === "string") {
          setPath(body.path);
          if ("steps" in body && Array.isArray(body.steps)) {
            setSteps(body.steps as { position: number; title: string; path: string; open: boolean }[]);
          }
        }
      })
      .catch(() => setPath(null));
  }, [lessonId]);

  if (path === null) {
    return <p className="empty">O pacote ainda não abriu.</p>;
  }
  return (
    <div>
      {steps.length > 1 ? (
        <ol>
          {steps.map((step) => (
            <li key={step.position}>
              <button type="button" disabled={!step.open} onClick={() => { setPosition(step.position); setPath(step.path); }}>
                {step.title}
              </button>
            </li>
          ))}
        </ol>
      ) : null}
      <iframe
        ref={frame}
        title="Pacote da aula"
        sandbox="allow-scripts"
        src={`/catalogo/aulas/${lessonId}/scorm/${path}`}
      />
    </div>
  );
}
