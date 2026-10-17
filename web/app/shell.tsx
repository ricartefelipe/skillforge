import Link from "next/link";
import type { ReactNode } from "react";

import type { Session } from "../lib/access";
import { deskOf } from "../lib/desk";
import { ThemeToggle } from "./theme";

export function Shell({ session, children }: { session: Session | null; children: ReactNode }) {
  return (
    <div className="shell">
      <aside className="rail" data-role={session?.role}>
        <Link href="/" className="brand">
          <Mark />
          <span className="wordmark">
            <strong>Skillforge</strong>
            <small>oficina</small>
          </span>
        </Link>
        <ThemeToggle />
        <nav aria-label="Principal">
          {session ? (
            deskOf(session.role).nav.map((link) => (
              <Link key={link.href} href={link.href}>
                {link.label}
              </Link>
            ))
          ) : (
            <Link href="/login" className="enter">
              Entrar
            </Link>
          )}
        </nav>
        {session ? (
          <div className="rail-foot">
            <p className="who">
              {session.name}
              <span className="role">{session.label}</span>
            </p>
            <form action="/logout" method="post">
              <button type="submit" className="leave">
                Sair
              </button>
            </form>
          </div>
        ) : null}
      </aside>
      <div className="stage">
        <main className="workspace">{children}</main>
      </div>
    </div>
  );
}

function Mark() {
  return (
    <svg className="mark" viewBox="0 0 48 48" aria-hidden="true">
      <rect width="48" height="48" rx="14" fill="#3b82f6" />
      <path
        d="M13 31.5h22M15.5 31.5 19 22h10l3.5 9.5M21.5 22v-6h5v6"
        fill="none"
        stroke="#f8fbff"
        strokeWidth="2.3"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <circle cx="34" cy="14" r="2.4" fill="#f8fbff" />
    </svg>
  );
}
