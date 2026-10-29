import { apiUrl } from "./openid";

/**
 * Bilhete de leitura dos arquivos de um pacote de aula.
 *
 * O pacote roda em um iframe de origem opaca, e um documento de origem opaca não é "same-site" com
 * nada: o cookie de sessão não acompanha os sub-recursos que a página do pacote pede. O caminho dos
 * arquivos carrega então um bilhete, emitido pela API com a sessão já verificada, preso a uma aula e
 * com expiração. Ele vive na API (Postgres), e por isso qualquer instância — da API ou deste site —
 * honra um bilhete que outra emitiu. Como viaja no caminho, os endereços relativos do pacote o
 * herdam sem reescrita. Revogação: vence em uma hora, e apagar a aula apaga seus bilhetes.
 */

export async function issueScormTicket(lessonId: string, token: string): Promise<string | null> {
  const response = await fetch(`${apiUrl()}/lessons/${lessonId}/scorm/ticket`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!response.ok) {
    return null;
  }
  const body = (await response.json()) as { ticket?: unknown };
  return typeof body.ticket === "string" ? body.ticket : null;
}

export function scormFileBase(lessonId: string, ticket: string): string {
  return `/catalogo/aulas/${lessonId}/scorm-arquivo/${ticket}/`;
}

/** Endereço, na API, de um arquivo do pacote lido com o bilhete em vez da sessão. */
export function scormFileUrl(lessonId: string, ticket: string, path: string): string {
  return `${apiUrl()}/lessons/${lessonId}/scorm/files/${ticket}?path=${encodeURIComponent(path)}`;
}
