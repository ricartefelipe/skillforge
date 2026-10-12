export type ProbeReading = "pending" | "up" | "down";

export function readingFromProbe(httpOk: boolean, body: unknown): Exclude<ProbeReading, "pending"> {
  if (!httpOk || body === null || typeof body !== "object" || !("status" in body)) {
    return "down";
  }

  return body.status === "UP" ? "up" : "down";
}

export function headline(liveness: ProbeReading, readiness: ProbeReading): string {
  if (liveness === "pending" || readiness === "pending") {
    return "Consultando o ambiente.";
  }

  if (liveness === "down") {
    return "O ambiente não responde.";
  }

  if (readiness === "down") {
    return "O processo vive. Ainda não atende.";
  }

  return "O ambiente está de pé.";
}

export function gaugeLabel(reading: ProbeReading, up: string, down: string): string {
  if (reading === "pending") {
    return "consultando";
  }

  return reading === "up" ? up : down;
}
