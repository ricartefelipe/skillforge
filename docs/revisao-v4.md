# Revisão V4 — Skillforge

Trabalho de auditoria dirigida e correção local sobre o checkout atual. Pronto para revisão e
versionamento pelo proprietário. O controle de Git, tags, releases e publicação permanece com o dono;
nada foi commitado, enviado ou publicado nesta tarefa.

## Contexto do checkout

- Branch de trabalho: `feature/melhoria-refactor` em `b30b9d9` (conteúdo idêntico ao snapshot `main`
  `ab1fc6b`; `git diff ab1fc6b HEAD` vazio antes das correções). A branch não foi trocada.
- Versão: `0.14.16` (VERSION, `api/pom.xml`, `web/package.json` concordam — gate da CI preservado).
- Data da revisão: 2026-10-27.
- Pilha: API Spring Boot 4.1.1 / Java 21; web Next.js 16; Keycloak 26.4; Postgres 16; Docker Compose.
  Testes de API usam Testcontainers (Postgres real).

## Comandos reais executados (evidência local)

| Gate | Comando | Resultado |
| --- | --- | --- |
| API (baseline) | `cd api && ./mvnw -B verify` | BUILD SUCCESS — 93 testes, 0 Checkstyle |
| API (após correções) | `cd api && ./mvnw -B verify` | BUILD SUCCESS — **97 testes**, 0 Checkstyle |
| Web (full) | `cd web && npm ci && npm run lint && npm run typecheck && npm test && npm run build` | exit 0 — **66 testes**, build compilado |
| Dependências web | `cd web && npm audit` | **0 vulnerabilidades** (antes: 1 moderada + 3 críticas) |
| Diff | `git diff --check` | sem whitespace/marcadores de conflito |
| Script | `shellcheck scripts/start.sh` | **não executável localmente** (binário ausente); `scripts/start.sh` não foi alterado |

Ambiente: Java 21.0.12, Maven 3.10.0 (wrapper), Node 24.15, npm 12.0.2, Docker 29.8. Toda a execução é
local; nenhuma CI remota foi disparada. A CI remota do próximo commit depende de publicação pelo dono e
não é atestada aqui.

## Matriz única de fechamento

Legenda de estado: **Corrigido** (nesta execução) · **Já atendido** (comprovado, preservado) ·
**Documentado** (pendência local com plano e razão) · **Bloqueio externo** (depende de insumo do dono).

| ID | Requisito / achado | Evidência atual | Implementação | Prova | Estado |
| --- | --- | --- | --- | --- | --- |
| P0-1 | Gabarito não pode sair ao aluno (README: "O gabarito não sai para o aluno") | `GET /cohorts/{id}` já escondia `correct` do aluno, mas `GET /grades` montava `ReportBody.from` com `revealKey=true` fixo → `choices[].correct` vazava ao aluno | `CohortController.grades` passa `revealKey = role != ALUNO`; `ReportBody.from(report, revealKey)` | Novo teste `theReportNeverRevealsTheAnswerKeyToTheStudent` (aluno não vê `correct`; docente vê) | **Corrigido** |
| P1-2 | SCORM hostil / zip bomb (closing #6, cenário 6) | `ScormZip.read` limitava 500 entradas, mas `readAllBytes` por entrada não limitava bytes inflados | Leitura limitada acumulada `read(zip, maxTotalBytes)` (teto 256 MB), rejeita `too-large`; XXE já tratado (`disallow-doctype-decl`), traversal já tratado | Novo teste `refusesAPackageThatInflatesBeyondTheBudget` | **Corrigido** |
| P1-3 | LTI 1.3: lançamento não pode dar acesso por URL (closing #4) | `CatalogApplication.authorize` refletia `target_link_uri` da query sem validar contra o tool da lição; id_token assinado (sub=usuário) seria POSTado a URL arbitrária | Exige `target == courses.toolUrl(lessonId)` (lição vem do hint, já one-time + expiry) | Novo teste `aModernLtiLaunchOnlyPostsToTheRegisteredTool` (target válido = 200; `evil.example` = 400 `launch`) | **Corrigido** |
| P1-4 | Conteúdo de professor não pode roubar sessão (closing #4/#6, cenário 6) | HTML do pacote servido na **mesma origem** do web app, em `<iframe>` **sem `sandbox`** → session-riding/XSS (cookie httpOnly, mas fetch credenciado + DOM pai) | `iframe sandbox="allow-scripts"` (origem opaca); runtime SCORM entregue dentro do frame (shim injetado em `scorm/[...path]/route.ts`) e commit relayado por `postMessage` ao player | `lib/scorm.test.ts` (injeção, `isHtml`, leitura de commit); typecheck+build verdes | **Corrigido** (ver nota de verificação em navegador) |
| SKILLFORGE-01 | Migração versionada, sem DDL concorrente no 1º request (closing #2, cenário 1) | `Schema.ensure()` roda `catalog.sql` preguiçoso por processo, sem lock nem versão | `pg_advisory_lock` serializa o DDL entre instâncias; ledger `schema_history(checksum)` aplica script idêntico uma vez e registra; preserva boot-sem-DB da readiness; `catalog.sql` já é incremental (ALTER … IF NOT EXISTS) | `ReadinessWhenDatabaseIsUpTest` (Postgres real) e demais testes de persistência verdes com o novo caminho | **Corrigido** |
| SKILLFORGE-02 | Amplificação de memória no upload 512 MB e entrega de mídia (closing #3, cenário 2) | Multipart já derrama em disco (`fileSizeThreshold=0`); a entrega era `ResponseEntity<byte[]>` sem `Range`, e o proxy do site bufferizava o arquivo inteiro | **Entrega com `Range`**: API responde 206/`Content-Range`/`Accept-Ranges` em `media` e `assets` (player busca/retoma por partes); proxy do site repassa o `Range` e **transmite o corpo em stream** (não bufferiza mais o arquivo). Pico de heap por requisição resolvido com leitura e escrita em stream sobre `bytea` (ver "Pico de heap por requisição"); dados antigos medidos e com procedimento de reescrita opcional (ver "Dados gravados antes da mudança") | `mediaAnswersARangeRequestWithTheAskedBytes`, `SlicedMediaTest`, `lib/media.test.ts`; medições com `-Xmx256m` | **Corrigido e medido** |
| DEP-1 | Vulnerabilidade de dependência de runtime | `next@16.4.0` afetado por 3 advisories **críticas** (RCE no Image Optimization com SVG; DoS em Server Actions; RCE em `use cache`) — bloqueava `npm run build` | Upgrade compatível no mesmo major: `next`/`eslint-config-next` → `^16.4.3` | `npm audit` sem o Next; gate web verde | **Corrigido** |
| DEP-2 | Vulnerabilidade de dependência de desenvolvimento | `vitest@3.2.7` → `tinypool`/`@vitest/mocker` (2 críticas + 1 moderada), só no runner de testes | Upgrade major com prova de compatibilidade: `vitest` → `^5.0.5` (e `@vitejs/plugin-react` 5.2.0); suíte revalidada | `npm audit` = **0 vulnerabilidades**; 62 testes verdes | **Corrigido** |
| SKILLFORGE-03 | Fluxo real existe (cursos, turmas, provas, certificados) | Jornadas presentes e cobertas por testes de integração | Auditado sem duplicar telas/modelos; correções cirúrgicas | 96 testes de API verdes | **Já atendido** |
| SKILLFORGE-04 | Ownership de curso/turma e papéis | `CohortGate.requireLead`/`enrolled`/`canSee`, `CourseAccess`, `requireWriter`; prova negativa com dois atores nos testes | Preservado | `CohortAccessTest`/`CatalogAccessTest` (turma alheia = 404; docente só edita as suas) | **Já atendido** |
| AUD-aud | Audiência/issuer/expiração do token | `SkillforgeTokens.validator` exige `aud=skillforge` + validadores default com issuer | Preservado | `SkillforgeTokensTest`, `MeAccessTest` | **Já atendido** |
| AUD-clock | Relógio da prova no servidor (cenário 4) | `exam_clock` grava `started_at`; `gradeOf` usa prazo do servidor; browser não aumenta o tempo | Preservado | `CohortAccessTest` (not-started/started), `ExamApplicationTest` | **Já atendido** |
| AUD-attempts | Tentativas/idempotência de envio (cenário 4) | `sitting` UNIQUE(exam,student,ordinal) barra corrida; `attempt` chave (exam,student) não duplica nota; `attempts_allowed` | Preservado | `CohortAccessTest` (already-taken), schema | **Já atendido** |

## Amplificação de upload (SKILLFORGE-02) — medição e decisão

- `UploadCeiling` = 512 MB; `MultipartConfigElement(..., fileSizeThreshold=0)` → o multipart é **gravado em disco**
  na análise (não fica 512 MB em heap no parse).
- No ingresso (`CatalogController.createLesson`/`addAsset`): `file.getBytes()` materializa o arquivo inteiro em
  um `byte[]` (heap). `LessonContent.Accepted` faz cópia defensiva no construtor e em `media()`; a persistência
  lê `accepted.media()` (nova cópia). Pico transitório ≈ 2–3× o tamanho do arquivo, **uma vez por upload**.
- No serviço (`/lessons/{id}/media`, `/assets/{id}`, `/scorm/file`): `ResponseEntity<byte[]>` carrega o blob
  inteiro em heap **a cada download**, sem suporte a `Range`.
- Decisão: a causa de pressão sustentada é o caminho de **serviço**. Trocar para streaming a partir de
  `getBinaryStream` com `Range` exige transação aberta durante o stream e validação de performance contra
  Postgres real (risco de regredir um recurso funcionando sem E2E de arquivo grande). Tratado como pendência
  documentada com plano (abaixo), não reescrito às cegas. A superfície de inflação por descompressão (SCORM)
  — a mais explorável — foi fechada em P1-2.

## Pico de heap por requisição — resolvido e medido (2026-10-29)

A pendência era a materialização do arquivo inteiro em heap, no ingresso e no serviço. Ela foi
eliminada **sem trocar o armazenamento**: `bytea` continua sendo o armazenamento, e o que mudou foi
como ele é lido e escrito. Não houve migração de dados nem alteração de esquema além de
`SET STORAGE EXTERNAL` nas três colunas de mídia, que só afeta gravações novas e deixa os valores
existentes legíveis como estão (o que isso custa em dados antigos está medido em "Dados gravados
antes da mudança", abaixo).

- **Decisão de tipo sem o arquivo.** `LessonContent.decide` recebe um `MediaProbe` — os primeiros
  512 bytes e o tamanho declarado — em vez do `byte[]` completo. As assinaturas reconhecidas olham
  no máximo 200 bytes, e o teto é conferido pelo tamanho, então a decisão é a mesma de antes.
- **Ingresso em stream.** `MultipartMedia` abre o arquivo que o contêiner já derramou em disco, e o
  repositório grava com `setBinaryStream`. Nenhuma cópia do conteúdo passa por `byte[]`.
- **Serviço por faixa.** `SlicedMedia` lê `substring(media from ?::int for ?::int)` em pedaços de
  256 KB, por demanda, conforme o cliente consome. O pico de um download acompanha o pedaço.
- **SCORM gravado entrada por entrada.** `ScormZip.read` entrega cada entrada a um `EntryWriter`
  assim que a lê, de modo que o pacote descompactado (teto de 256 MB) não se acumula em memória.
- **`LessonFile` foi removido**: não havia mais quem carregasse o arquivo inteiro.

### Medição

Pilha local completa: Postgres 16 e Keycloak 26.4 em Compose, API empacotada rodando com
`-Xmx256m -Xms256m` (heap **menor que o arquivo**, de propósito: materializar seria OOM garantido).

| Operação | Tamanho | Concorrência | Pico de heap usado | Resultado |
| --- | --- | --- | --- | --- |
| Upload de vídeo (`POST /modules/{id}/lessons`) | 480 MiB | 1 | **72,2 MB** | HTTP 201 em 10 s |
| Download completo (`GET /lessons/{id}/media`) | 480 MiB | 1 | — | HTTP 200, 4 s, bytes idênticos ao original |
| Download completo, concorrente | 480 MiB cada | **6** (2,88 GiB no total) | **51,7 MB** | seis HTTP 200 de 503.316.480 bytes |

Heap medido com `jcmd GC.heap_info` a cada segundo (1.213 amostras no download). Zero
`OutOfMemoryError` no log, e a API seguiu respondendo `readiness` 200 ao fim.

### Faixas e borda, contra o servidor real

| Pedido | Resposta |
| --- | --- |
| `bytes=100-199` | 206, `Content-Range: bytes 100-199/503316480`, 100 bytes |
| `bytes=503316300-` (aberto) | 206, `bytes 503316300-503316479/503316480`, 180 bytes |
| `bytes=-50` (sufixo) | 206, `bytes 503316430-503316479/503316480`, 50 bytes |
| `bytes=600000000-600000100` (além do fim) | **416** com `Content-Range: bytes */503316480` |
| `paginas=1-2` (unidade desconhecida) | **200** com o arquivo inteiro — a RFC 7233 manda ignorar |

Os bytes de uma faixa conferem com o arquivo original (`bytes=1000-1009` → `7b828990979ea5acb3ba`
nos dois lados). Cinco downloads interrompidos no meio não deixaram erro no log nem derrubaram a
API. O teste `SlicedMediaTest` prova a leitura parcial de forma determinística: ler 100 bytes de um
arquivo de 512 MB faz **uma** consulta ao armazenamento.

### Dados gravados antes da mudança — medidos, sem reescrita no boot

Cenário montado de propósito: banco novo com o **esquema anterior** (`catalog.sql` do último release,
sem `SET STORAGE EXTERNAL`), duas aulas de 256 MiB gravadas por ele — uma de zeros (o Postgres a
comprime por `pglz` para 3 MB em disco) e uma aleatória (incompressível) — e só então o upgrade para
esta versão. O upgrade aplicou o `catalog.sql` novo **sem tocar nas linhas** (`n_tup_upd = 0`,
tamanho da tabela igual, `schema_history` com um registro, `attstorage` de `x` para `e`): nada é
reescrito na subida.

| Medição (API com `-Xmx256m`, duas instâncias no mesmo banco) | Resultado |
| --- | --- |
| Download completo das duas aulas antigas | 200, 268.435.456 bytes cada, SHA-256 igual ao original |
| Faixas (`bytes=0-99`, meio, sufixo, além do fim) nas aulas antigas | 206 corretos e 416 no além-do-fim |
| 4 downloads concorrentes de aulas antigas (2 comprimidas, 2 aleatórias) | 4 × 200 com hash correto, **pico de heap 70,8 MB**, zero OOM |
| Download completo da aula **aleatória** antiga | 3 s |
| Download completo da aula **comprimida** antiga | **222 s** (99–142 s com cache quente) |

A conclusão é que dados antigos **funcionam e cabem na memória**, mas um valor que o Postgres
comprimiu antes da mudança é lento para ler por faixa: cada `substring` descomprime o valor desde o
início, e um arquivo de 256 MiB em pedaços de 256 KB o descomprime mil vezes. Valores
incompressíveis (vídeo, áudio, imagem — o caso real) não sofrem disso; o cenário de zeros é o pior
caso.

**Procedimento de transformação — `scripts/rewrite-media-storage.sh`** (opcional, para operador):

- Recuperável e limitado por lote: `rewrite-media-storage.sh <dsn> [limite]` reescreve até `limite`
  linhas por tabela (`lesson`, `lesson_asset`, `scorm_file`), **uma transação por linha**; pode ser
  interrompido e retomado em qualquer ponto, e só escolhe linhas ainda comprimidas
  (`pg_column_size < octet_length`), então a segunda execução não faz nada.
- Com verificação de integridade: cada linha é reescrita dentro de um bloco que compara o `md5` do
  conteúdo antes e depois e confere que o valor deixou de estar comprimido; qualquer divergência
  aborta a transação daquela linha e o valor antigo permanece.
- Medido no cenário acima: a aula comprimida foi reescrita em **2 s** com `md5` conferido; o download
  completo caiu de 107–222 s para **2 s** e uma faixa no meio do arquivo para **38 ms**; a segunda
  execução terminou com `0` linhas restantes nas três tabelas. A linha antiga fica para o `VACUUM`
  (tabela de 269 MB para 534 MB até lá).

## Inventário de arquivos

Modificados:

- `api/src/main/java/skillforge/infrastructure/persistence/Schema.java` — advisory lock + ledger de checksum.
- `api/src/main/java/skillforge/domain/catalog/ScormZip.java` — leitura limitada (anti zip bomb).
- `api/src/main/java/skillforge/interfaces/http/CohortController.java` — `revealKey` por papel no relatório.
- `api/src/main/java/skillforge/application/catalog/CatalogApplication.java` — bind do target LTI ao tool.
- `api/src/main/java/skillforge/interfaces/http/CatalogController.java` — entrega de mídia com `Range`/206.
- `api/src/test/java/skillforge/CohortAccessTest.java` — testes de gabarito e de lançamento LTI.
- `api/src/test/java/skillforge/CatalogAccessTest.java` — teste de requisição `Range`.
- `api/src/test/java/skillforge/domain/catalog/ScormZipTest.java` — teste de orçamento de inflação.
- `web/app/catalogo/scorm-player.tsx` — iframe `sandbox` + ponte `postMessage`.
- `web/app/catalogo/aulas/[id]/scorm/[...path]/route.ts` — injeção da ponte em HTML servido.
- `web/app/catalogo/aulas/[id]/arquivo/route.ts` — repasse de `Range` + stream da mídia.
- `web/app/catalogo/aulas/[id]/arquivos/[assetId]/route.ts` — repasse de `Range` + stream do anexo.
- `web/package.json`, `web/package-lock.json` — `next@^16.4.3`, `vitest@^5.0.5` (correção de advisories).

Novos:

- `web/lib/scorm.ts` — ponte SCORM (shim, injeção, leitura de commit).
- `web/lib/scorm.test.ts` — testes da ponte.
- `web/lib/media.ts` — repasse de `Range` e stream do proxy de mídia.
- `web/lib/media.test.ts` — testes do proxy de mídia.

Sem arquivos removidos. `git diff --check` limpo. O build não depende de arquivo oculto fora do conjunto
(os fixtures de teste são gerados em código). Nenhuma credencial, dump real ou cache entra no pacote.

## Instalação, upgrade e recuperação

- Instalação/execução local: `scripts/start.sh` (sobe Compose: Postgres, Keycloak, API, web) com `.env`
  a partir de `.env.example`. Readiness por condição (healthchecks do Compose), sem sleeps.
- Upgrade de esquema: na primeira chamada de dados, `Schema.ensure()` toma `pg_advisory_lock`, aplica
  `catalog.sql` (incremental e idempotente) uma vez por checksum e registra em `schema_history`. Duas
  instâncias subindo juntas **não** executam DDL concorrente (o lock serializa). O boot continua possível
  com Postgres indisponível (liveness responde; readiness fica fora até o banco voltar).
- Recuperação: backup/restore deve validar pela aplicação (autenticação, leitura, permissões, totais,
  índices) em banco novo/isolado, com efeitos externos (e-mail) desligados até validar. Não reutilizar
  volume de uso real para fingir banco vazio.

## Bloqueios externos / ações exclusivas do proprietário

- Homologação de LTI/SCORM com ferramenta/pacote reais de terceiros exige credenciais de sandbox do dono.
  As proteções foram implementadas e testadas localmente (assinatura/one-time/expiry/target); a homologação
  conjunta depende do parceiro.
- Keycloak real com os três papéis em E2E de ponta a ponta depende de ambiente do dono (os testes cobrem as
  rotas e a autorização com JWT; o IdP real é dependência de operação).
- **DEP-1/DEP-2 já corrigidos** no código; a CI remota só reflete isso após o dono publicar o commit.
- Git/tags/releases/deploy e a desativação de qualquer automação perigosa já publicada permanecem com o dono.

## P2/P3 registrados (avaliar, não ampliar o fechamento)

- P2: rubricas/feedback detalhado com privacidade; SSO institucional e importação de matrículas; legendas/
  transcrições; white-label/i18n; streaming de mídia com Range (ver pendência) — esforço médio, depende de
  operação. P3: proctoring invasivo, reescrita do LMS, IA corrigindo sem rubrica, microsserviços/K8s — não
  justificados pela carga atual.

## Pacote SCORM em navegador real — executado, com um defeito corrigido

Pacote sintético (manifesto com dois itens, HTML, JS, CSS e PNG) enviado pela API e aberto em
Chromium pelo Playwright, com login real no Keycloak local. **15 de 15 verificações passam.**

O navegador revelou um defeito que os testes de unidade não alcançavam: os **sub-recursos do pacote
não carregavam**. O HTML abria (navegação do iframe, iniciada pela aplicação, leva a sessão), mas
`assets/runtime.js`, `assets/estilo.css` e `assets/marca.png` voltavam **307** para o login. A causa
é o próprio isolamento: um documento de origem opaca não é "same-site" com nada, e o cookie de
sessão, que é `lax`, não acompanha os sub-recursos que ele pede.

Abrir o `sandbox` resolveria o carregamento e devolveria ao conteúdo do professor o acesso à sessão
— exatamente o que o sandbox existe para impedir. Um cookie `SameSite=None` também foi medido e
**rejeitado**: o navegador o aceitou mas não o enviou, porque isso depende da política de cookies de
terceiros, que o navegador do aluno pode negar.

A correção foi um **bilhete de leitura no caminho**: ao servir o HTML, a rota emite um bilhete
(válido só para aquela aula, com expiração) e injeta `<base href=".../scorm-arquivo/{bilhete}/">`.
Os endereços relativos do pacote passam a herdar o bilhete sem que nada seja reescrito, e a
autorização continua a mesma — o bilhete é emitido com a sessão já verificada.

| Verificação | Resultado |
| --- | --- |
| Login pelo Keycloak (código de autorização) conclui | OK |
| Sub-recursos JS, CSS e PNG carregam e executam | OK (eram 307 antes) |
| O pacote encontra o runtime LMS dentro do sandbox | OK |
| `parent.location` e `parent.document` bloqueados | OK |
| O pacote não vê o cookie de sessão | OK |
| `window.origin` é `null` (origem opaca) | OK |
| Progresso registrado pelo commit do pacote | OK (segundo passo abre) |
| Progresso sobrevive ao recarregamento (retomada) | OK |
| Segundo passo do pacote carrega | OK |

### Bilhete em banco, válido entre instâncias (2026-10-29)

O bilhete deixou de viver no processo que o emitiu. A API o guarda na tabela `scorm_ticket`
(`token`, `lesson_id`, `username`, `expires_at`) e o site só o repassa:

- **Emissão:** `POST /lessons/{id}/scorm/ticket` (201, `{ "ticket": … }`) exige a sessão e a mesma
  autorização de abrir a aula (`scormLaunch`); o bilhete nasce com 1 hora de validade e os expirados
  são varridos a cada emissão.
- **Leitura:** `GET /lessons/{id}/scorm/files/{ticket}?path=…` é pública, mas só responde ao par
  (bilhete, aula) ainda válido; outra aula com o mesmo bilhete é 404, bilhete inexistente é 404,
  caminho com `..` é 400. A resposta sai com `Cache-Control: private, no-store` (nenhum proxy ou
  cache compartilhado guarda o arquivo) e `Referrer-Policy: no-referrer`; o HTML injetado leva também
  `<meta name="referrer" content="no-referrer">`, então o endereço com o bilhete não vaza em `Referer`
  para recursos externos do pacote. O bilhete não é escrito em log pela API nem pelo site.
- **Revogação:** remover a aula apaga os bilhetes dela; expirar é a revogação normal. Para revogar
  antes do prazo, `DELETE FROM scorm_ticket WHERE token = …` (ou `WHERE username = …` para um aluno);
  a próxima leitura é 404 e o player reemite ao reabrir a aula.
- **Cobertura:** `ScormTicketAccessTest` (4 testes contra Postgres real): bilhete de outra aula
  rejeitado, expirado rejeitado, caminho fora do pacote rejeitado, e emissão exige acesso à aula.

**Duas instâncias sem afinidade, provado em navegador.** Duas APIs (18081/18082) e dois sites
(13301/13302) sobre o mesmo banco, atrás de um nginx em *round-robin* sem afinidade; Chromium com
login real no Keycloak. **15 de 15 verificações passam**, e o log do nginx mostra cada pedido caindo
numa instância diferente: o HTML (`passo1.html`) veio de 13301, `estilo.css` e `marca.png` de 13302,
`runtime.js` de 13301, o commit de 13301 e `passo2.html` de 13302 — todos 200/204 com o mesmo bilhete.
Com `curl`, um bilhete emitido pela API 18081 foi aceito pela 18082 (200 `text/javascript`); o mesmo
bilhete em outra aula deu 404, um bilhete forjado 404 e `..` no caminho 400.

## Identidade: Keycloak local, login e papéis

| Prova | Resultado |
| --- | --- |
| `aluno`, `docente` e `administracao` obtêm token no realm `skillforge` | OK (`aud=skillforge`, papel correto em `realm_access`) |
| `GET /courses` sem token | 401 |
| `GET /courses` com token inválido | 401 |
| `GET /courses` como `aluno` | 200 |
| `POST /courses` como `aluno` | 403 |
| `POST /courses` como `docente` | 403 |
| `POST /courses` como `administracao` | 201 |

O realm versionado não foi alterado. Para obter tokens por senha e apontar o `redirect_uri` à porta
local, o cliente foi ajustado **apenas no Keycloak descartável desta execução**.
