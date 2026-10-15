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
        const status = values.get("cmi.core.lesson_status") ?? "incomplete";
        const raw = values.get("cmi.core.score.raw");
        const score = raw === undefined || raw.length === 0 ? null : Number(raw);
        void fetch(`/catalogo/aulas/${lessonId}/scorm/commit`, {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ status, score: Number.isInteger(score) ? score : null }),
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
        }
      })
      .catch(() => setPath(null));
    return () => {
      delete (window as Window & { API?: Api }).API;
    };
  }, [lessonId]);

  if (path === null) {
    return <p className="empty">O pacote ainda não abriu.</p>;
  }
  return <iframe title="Pacote da aula" src={`/catalogo/aulas/${lessonId}/scorm/${path}`} />;
}
