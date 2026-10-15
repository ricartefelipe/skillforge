"use client";

import { useEffect, useState } from "react";

type Api = {
  LMSInitialize: () => string;
  LMSFinish: () => string;
  LMSGetValue: (key: string) => string;
  LMSSetValue: (key: string, value: string) => string;
  LMSCommit: () => string;
  LMSGetLastError: () => string;
  LMSGetErrorString: () => string;
  LMSGetDiagnostic: () => string;
};

export function ScormPlayer({ lessonId }: { lessonId: string }) {
  const [path, setPath] = useState<string | null>(null);
  const [position, setPosition] = useState(1);
  const [steps, setSteps] = useState<{ position: number; title: string; path: string; open: boolean }[]>([]);

  useEffect(() => {
    const values = new Map<string, string>();
    const api: Api = {
      LMSInitialize: () => "true",
      LMSFinish: () => "true",
      LMSGetValue: (key) => values.get(key) ?? "",
      LMSSetValue: (key, value) => {
        values.set(key, value);
        return "true";
      },
      LMSCommit: () => {
        const status = values.get("cmi.core.lesson_status")
          ?? values.get("cmi.completion_status")
          ?? "incomplete";
        const raw = values.get("cmi.core.score.raw") ?? values.get("cmi.score.raw");
        const score = raw === undefined || raw.length === 0 ? null : Number(raw);
        void fetch(`/catalogo/aulas/${lessonId}/scorm/commit`, {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ status, score: Number.isInteger(score) ? score : null, position }),
        });
        return "true";
      },
      LMSGetLastError: () => "0",
      LMSGetErrorString: () => "",
      LMSGetDiagnostic: () => "",
    };
    (window as Window & { API?: Api }).API = api;
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
    return () => {
      delete (window as Window & { API?: Api }).API;
    };
  }, [lessonId, position]);

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
      <iframe title="Pacote da aula" src={`/catalogo/aulas/${lessonId}/scorm/${path}`} />
    </div>
  );
}
