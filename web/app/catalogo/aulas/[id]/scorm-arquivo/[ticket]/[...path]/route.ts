import { NextResponse } from "next/server";

import { injectScormBridge, isHtml } from "../../../../../../../lib/scorm";
import { scormFileBase, scormFileUrl } from "../../../../../../../lib/scorm-ticket";

/**
 * Arquivos de um pacote de aula, autorizados pelo bilhete do caminho.
 *
 * Quem pede é a página do pacote, que roda em origem opaca e por isso não apresenta a sessão. O
 * bilhete é conferido pela API — que o emitiu com a sessão verificada e o guarda em banco — de modo
 * que qualquer instância deste site serve o que outra emitiu. O conteúdo do professor não recebe
 * acesso algum à aplicação: o bilhete só lê os arquivos daquela aula, e vence.
 */
export async function GET(
  _request: Request,
  context: { params: Promise<{ id: string; ticket: string; path: string[] }> },
) {
  const { id, ticket, path } = await context.params;
  const response = await fetch(scormFileUrl(id, ticket, path.join("/")), { cache: "no-store" });
  if (!response.ok) {
    return new NextResponse(null, { status: response.status });
  }
  const type = response.headers.get("content-type") ?? "application/octet-stream";
  const headers = { "content-type": type, "cache-control": "private, no-store", "referrer-policy": "no-referrer" };
  // Uma página alcançada por link interno do pacote também precisa do runtime e da base.
  if (isHtml(type)) {
    return new NextResponse(injectScormBridge(await response.text(), scormFileBase(id, ticket)), { status: 200, headers });
  }
  return new NextResponse(await response.arrayBuffer(), { status: 200, headers });
}
