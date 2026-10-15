"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

import type { Session } from "../lib/access";
import { gaugeLabel, headline, readingFromProbe, type ProbeReading } from "../lib/probes";
import { Shell } from "./shell";

async function consult(path: string): Promise<ProbeReading> {
  try {
    const response = await fetch(path, { cache: "no-store" });
    const body: unknown = await response.json();
    return readingFromProbe(response.ok, body);
  } catch {
    return "down";
  }
}

export function SkillforgeHome({
  session,
  resume = null,
}: {
  session: Session | null;
  resume?: { href: string; title: string } | null;
}) {
  const [liveness, setLiveness] = useState<ProbeReading>("pending");
  const [readiness, setReadiness] = useState<ProbeReading>("pending");
  const request = useRef(0);

  const load = useCallback((current: number) => {
    void consult("/health/liveness").then((reading) => {
      if (request.current === current) {
        setLiveness(reading);
      }
    });
    void consult("/health/readiness").then((reading) => {
      if (request.current === current) {
        setReadiness(reading);
      }
    });
  }, []);

  const refresh = useCallback(() => {
    const current = request.current + 1;
    request.current = current;
    setLiveness("pending");
    setReadiness("pending");
    load(current);
  }, [load]);

  useEffect(() => {
    const current = request.current + 1;
    request.current = current;
    load(current);
  }, [load]);

  return (
    <Shell session={session}>
      <div className={`page reading-${liveness} readiness-${readiness}`}>
        <header className="intro">
          <p className="kicker">Ambiente</p>
          <h1 aria-live="polite">{headline(liveness, readiness)}</h1>
          <p className="lede">Construção de competências pela prática.</p>
        </header>
        {resume ? (
          <p>
            <Link href={resume.href}>Continuar: {resume.title}</Link>
          </p>
        ) : null}
        <section className="panel access" aria-label="Acesso">
          {session ? (
            <article>
              <h2>Acesso</h2>
              <p className="who">{session.name}</p>
              {session.name.localeCompare(session.label, "pt-BR", { sensitivity: "accent" }) === 0 ? null : (
                <p className="role">{session.label}</p>
              )}
            </article>
          ) : (
            <article>
              <h2>Acesso</h2>
              <p className="prompt">Entre com a sua conta.</p>
            </article>
          )}
        </section>
        <section className="panel" aria-label="Estado do ambiente">
          <article aria-label="Vida" data-reading={liveness}>
            <h2>Vida</h2>
            <p>{gaugeLabel(liveness, "no ar", "fora do ar")}</p>
          </article>
          <article aria-label="Prontidão" data-reading={readiness}>
            <h2>Prontidão</h2>
            <p>{gaugeLabel(readiness, "pronta", "indisponível")}</p>
          </article>
        </section>
        <button type="button" onClick={refresh}>
          Consultar de novo
        </button>
      </div>
    </Shell>
  );
}
