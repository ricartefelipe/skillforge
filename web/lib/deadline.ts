const ZONE = "America/Sao_Paulo";

export function formatDeadline(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  const parts = new Intl.DateTimeFormat("pt-BR", {
    timeZone: ZONE,
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const day = part(parts, "day");
  const month = part(parts, "month");
  const hour = part(parts, "hour");
  const minute = part(parts, "minute");
  const clock = minute === "00" ? `${hour}h` : `${hour}h${minute}`;
  return `${day} de ${month}, ${clock}`;
}

export function deadlineDay(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso.slice(0, 10);
  }
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function part(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  return parts.find((item) => item.type === type)?.value ?? "";
}
