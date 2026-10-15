import type { Metadata } from "next";
import { Literata, Newsreader, Sora } from "next/font/google";
import type { ReactNode } from "react";

import "./globals.css";

const display = Newsreader({
  subsets: ["latin"],
  variable: "--font-display",
  weight: ["500", "600", "700"],
});

const literata = Literata({
  subsets: ["latin"],
  variable: "--font-literata",
  weight: ["400", "600", "700"],
});

const grotesk = Sora({
  subsets: ["latin"],
  variable: "--font-grotesk",
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: "Skillforge",
  description: "Construção de competências pela prática",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="pt-BR" className={`${display.variable} ${literata.variable} ${grotesk.variable}`}>
      <body>{children}</body>
    </html>
  );
}
