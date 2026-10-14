import Link from "next/link";
import type { ReactNode } from "react";

import type { Session } from "../lib/access";

export function Shell({ session, children }: { session: Session | null; children: ReactNode }) {
  return (
    <div className="shell">
      <header className="bar">
        <div className="bar-inner">
          <Link href="/" className="brand">
            <svg className="mark" viewBox="0 0 32 32" aria-hidden="true">
              <path d="M6 23.5 16 7l10 16.5" fill="none" stroke="currentColor" strokeWidth="2.2" />
              <path d="M10.5 23.5h11" stroke="currentColor" strokeWidth="2.2" />
              <circle cx="16" cy="16.5" r="1.7" fill="currentColor" />
            </svg>
            Skillforge
          </Link>
          <nav aria-label="Principal">
            {session ? (
              <>
                <Link href="/catalogo">Catálogo</Link>
                <Link href="/turmas">Turmas</Link>
                <span className="session">
                  <span className="who">{session.name}</span>
                  {session.name.localeCompare(session.label, "pt-BR", { sensitivity: "accent" }) === 0 ? null : (
                    <span className="role">{session.label}</span>
                  )}
                </span>
                <Link href="/logout">Sair</Link>
              </>
            ) : (
              <Link href="/login">Entrar</Link>
            )}
          </nav>
        </div>
      </header>
      <main className="workspace">{children}</main>
    </div>
  );
}
