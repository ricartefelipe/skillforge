"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import Link from "next/link";

import type { Session } from "../lib/access";
import { gaugeLabel, headline, readingFromProbe, type ProbeReading } from "../lib/probes";

async function consult(path: string): Promise<ProbeReading> {
  try {
    const response = await fetch(path, { cache: "no-store" });
    const body: unknown = await response.json();
    return readingFromProbe(response.ok, body);
  } catch {
    return "down";
  }
}

export function SkillforgeHome({ session }: { session: Session | null }) {
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
    <main className={`floor reading-${liveness} readiness-${readiness}`}>
      <p className="mark">Skillforge</p>
      <h1 aria-live="polite">{headline(liveness, readiness)}</h1>
      <p className="lede">Construção de competências pela prática.</p>
      <section className="plate access" aria-label="Acesso">
        {session ? (
          <article>
            <h2>Acesso</h2>
            <p className="who">{session.name}</p>
            <p className="role">{session.label}</p>
            <a href="/catalogo">Catálogo</a>
            <Link href="/turmas">Turmas</Link>
            <a href="/logout">Sair</a>
          </article>
        ) : (
          <article>
            <h2>Acesso</h2>
            <p className="prompt">Entre com a sua conta.</p>
            <a href="/login">Entrar</a>
          </article>
        )}
      </section>
      <section className="plate" aria-label="Estado do ambiente">
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
    </main>
  );
}
