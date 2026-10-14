import type { Metadata } from "next";
import { Familjen_Grotesk, Fraunces, Literata } from "next/font/google";
import type { ReactNode } from "react";

import "./globals.css";

const display = Fraunces({
  subsets: ["latin"],
  variable: "--font-display",
  weight: ["500", "600", "700"],
});

const literata = Literata({
  subsets: ["latin"],
  variable: "--font-literata",
  weight: ["400", "600", "700"],
});

const grotesk = Familjen_Grotesk({
  subsets: ["latin"],
  variable: "--font-grotesk",
  weight: ["500", "700"],
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
