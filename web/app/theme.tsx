"use client";

import { useSyncExternalStore } from "react";

const STORAGE = "skillforge-theme";
const EVENT = "skillforge-theme";

function subscribe(listener: () => void) {
  window.addEventListener(EVENT, listener);
  return () => window.removeEventListener(EVENT, listener);
}

function nightNow() {
  return document.documentElement.dataset.theme === "night";
}

function apply(night: boolean) {
  document.documentElement.dataset.theme = night ? "night" : "day";
  localStorage.setItem(STORAGE, night ? "night" : "day");
  window.dispatchEvent(new Event(EVENT));
}

export function ThemeToggle() {
  const night = useSyncExternalStore(subscribe, nightNow, () => false);

  return (
    <button
      type="button"
      className="theme"
      onClick={() => apply(!night)}
      aria-label={night ? "Visão clara" : "Visão noturna"}
      suppressHydrationWarning
    >
      {night ? <Moon /> : <Sun />}
    </button>
  );
}

function Sun() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="4" fill="currentColor" />
      <path
        d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.1 5.1l1.6 1.6M17.3 17.3l1.6 1.6M18.9 5.1l-1.6 1.6M6.7 17.3l-1.6 1.6"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  );
}

function Moon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M15.5 3.2a8.2 8.2 0 1 0 5.3 12.6A7.2 7.2 0 0 1 15.5 3.2Z" fill="currentColor" />
    </svg>
  );
}
