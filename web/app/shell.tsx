import Link from "next/link";
import type { ReactNode } from "react";

import type { Session } from "../lib/access";

export function Shell({ session, children }: { session: Session | null; children: ReactNode }) {
  return (
    <div className="shell">
      <header className="bar">
        <Link href="/" className="brand">
          Skillforge
        </Link>
        <nav aria-label="Principal">
          {session ? (
            <>
              <Link href="/catalogo">Catálogo</Link>
              <Link href="/turmas">Turmas</Link>
              <span className="who">{session.name}</span>
              {session.name.localeCompare(session.label, "pt-BR", { sensitivity: "accent" }) === 0 ? null : (
                <span className="role">{session.label}</span>
              )}
              <Link href="/logout">Sair</Link>
            </>
          ) : (
            <Link href="/login">Entrar</Link>
          )}
        </nav>
      </header>
      <div className="workspace">{children}</div>
    </div>
  );
}
