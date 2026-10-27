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
| SKILLFORGE-02 | Amplificação de memória no upload 512 MB e entrega de mídia (closing #3, cenário 2) | Multipart já derrama em disco (`fileSizeThreshold=0`); a entrega era `ResponseEntity<byte[]>` sem `Range`, e o proxy do site bufferizava o arquivo inteiro | **Entrega com `Range`**: API responde 206/`Content-Range`/`Accept-Ranges` em `media` e `assets` (player busca/retoma por partes); proxy do site repassa o `Range` e **transmite o corpo em stream** (não bufferiza mais o arquivo). Redução de pico de heap por requisição depende de migração de armazenamento (ver "Pendência documentada") | `mediaAnswersARangeRequestWithTheAskedBytes` (206 + bytes da faixa); `lib/media.test.ts` | **Corrigido (entrega); Documentado (armazenamento)** |
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

## Pendência documentada (local, com plano) — pico de heap por requisição

Já feito nesta entrega: `Range`/206 na API (`media`, `assets`) e stream no proxy do site. Isso resolve
busca/retomada e para de bufferizar o arquivo no proxy. **Falta** reduzir o pico de heap no serviço da API,
que ainda materializa o blob inteiro — inerente ao armazenamento em `bytea` (o driver do Postgres materializa
o `bytea` por completo mesmo com `getBinaryStream`).

- Objetivo: pico de heap por download proporcional ao chunk, não ao arquivo.
- Caminho: migrar o armazenamento de mídia de coluna `bytea` para **large objects** (`lo`/`oid`) ou
  armazenamento de arquivos/objetos, e então transmitir por chunk servindo apenas a faixa pedida.
- Passos: (1) migração versionada dos blobs existentes (com o ledger `schema_history`); (2) leitura por
  offset/length do large object dentro da transação; (3) teste de integração com arquivo grande e `Range`
  em Postgres real medindo heap; (4) opcional: reduzir cópias defensivas no ingresso para blobs grandes.
- Razão de não aplicar agora: é migração de **armazenamento com dados existentes**, que precisa de prova de
  performance com PG real e E2E de arquivo grande para não regredir a reprodução de vídeo/PDF.

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

## Verificação ainda recomendada em navegador

- P1-4 (sandbox SCORM): a lógica pura está testada (vitest) e o build/typecheck passam. A reprodução de um
  pacote SCORM real dentro do `iframe sandbox` com a ponte `postMessage` deve ser confirmada em navegador
  (carregamento de sub-recursos e gravação de progresso) antes de considerar o item encerrado em produção.
