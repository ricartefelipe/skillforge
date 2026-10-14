# Skillforge

Construção de competências pela prática.

O repositório, o pacote Java e a interface usam o mesmo nome: **skillforge**.

## Incremento 0

A aplicação sobe e responde duas sondas:

- **Vida** — o processo está no ar, mesmo se o Postgres não estiver.
- **Prontidão** — o processo atende. Se o Postgres não aceita conexão, a prontidão responde `503` e o tráfego deve ser recusado.

| Sonda | Caminho |
| --- | --- |
| Vida | `GET /actuator/health/liveness` |
| Prontidão | `GET /actuator/health/readiness` |

A interface em `http://localhost:3000` lê as duas sondas e diz se o ambiente está de pé.

## Incremento 1

O acesso entra pelo Keycloak, em `http://localhost:8180`. Há três contas: `aluno`, `docente` e `administracao`. Cada uma tem só o primeiro nome. O arranque ajusta o perfil para não exigir e-mail nem sobrenome. As senhas ficam no `.env` local, geradas no arranque.

- Sem sessão, `GET /me` responde `401`.
- Com um papel institucional, `GET /me` devolve o nome e o papel.
- Zero papéis, ou mais de um entre aluno, docente e administração, responde `403`.
- Vida e prontidão continuam sem sessão. A prontidão também recusa tráfego se o acesso não responde.

A interface mostra Entrar, ou o nome e o papel com Sair.

## Incremento 2

O catálogo tem cursos, módulos e aulas. Uma aula é markdown, um PDF ou um vídeo, e só uma dessas coisas.

- Sem sessão, `GET /courses` responde `401`.
- Aluno e docente leem. Só a administração cria.
- Nome em branco, curso repetido, arquivo que não é o tipo declarado, ou conteúdo acima do limite, são recusados.
- Com o Postgres fechado, o catálogo responde `503` e a vida continua `200`.

A interface lista o catálogo para quem entrou e só mostra os formulários para a administração.

## Incremento 3

A administração abre uma turma para um curso, indica a conta do docente e matricula alunos pela conta.

- Sem sessão, `GET /cohorts` responde `401`.
- O aluno vê somente as turmas em que está. O docente vê somente as que ministra. A administração vê todas.
- A aula do curso aparece para quem está na turma. Não há conclusão.
- Nome em branco, docente em branco, aluno em branco, turma repetida no mesmo curso ou matrícula repetida são recusados.
- Com o Postgres fechado, a turma responde `503` e a vida continua `200`.

A interface lista as turmas de quem entrou. Só a administração abre turma e matricula.

## Arranque

É preciso Java 21, Node.js 22, Docker e curl.

```bash
./scripts/start.sh
```

Na primeira execução o script cria um `.env` local, com senhas geradas, e sobe o Postgres, o acesso, a API e a interface. O `.env` não entra no repositório. O modelo vazio está em `.env.example`.

## Versão

`VERSION` é a versão do produto. O CI publica a tag anotada `vX.Y.Z` quando essa versão chega em `main`.
