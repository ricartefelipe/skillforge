# Skillforge

Construção de competências pela prática.

O repositório, o pacote Java e a interface usam o mesmo nome: **skillforge**.

## Quem entra

O acesso é pelo Keycloak. Há três contas: `aluno`, `docente` e `administracao`. Cada uma tem só o primeiro nome. Sem sessão, a API responde `401`. Zero papéis, ou mais de um entre esses três, responde `403`.

| Papel | O que faz |
| --- | --- |
| Aluno | Vê a grade das turmas em que está, conclui aula, entrega prática, faz a prova uma vez, abre dúvida e lê o próprio boletim e o certificado. |
| Docente | Ministra as próprias turmas. Publica módulo, material, videoaula, prática, prova, aviso e chamada. Devolve a nota. |
| Administração | Cria curso, abre turma, indica o docente e matricula. Vê o catálogo e as turmas inteiros. |

A interface tem Cursos, Aulas, Turmas e Boletim. Docente e administração também têm a bancada Docente.

## A turma

Um curso tem módulos. Cada aula é markdown, um PDF ou um vídeo, e só uma dessas coisas. A administração cria o curso. Quem ministra publica módulo e aula no curso da própria turma.

A administração abre a turma, indica a conta do docente e matricula pela conta. O aluno vê só as turmas em que está. O docente vê só as que ministra.

- O aluno marca a aula como concluída. A marca permanece. Marcar de novo não muda a conta.
- A prática tem enunciado, prazo opcional e rubrica opcional. O aluno entrega um texto. O docente devolve um comentário e a nota de 0 a 10. Com rubrica, a nota é a soma dos critérios. Depois da devolutiva, a entrega não muda.
- A prova tem prazo opcional. Cada questão tem ao menos duas alternativas e uma só correta. O gabarito não sai para o aluno. A tentativa é única. A nota é 0 a 10, na proporção das questões certas.
- Depois do prazo, a entrega da prática ou da prova é recusada.
- Na aula, quem está no curso abre uma dúvida e os outros respondem. Quem está fora recebe `404`.
- O docente publica o aviso da turma e registra a chamada do dia. O mesmo dia não se repete.
- O certificado sai para o aluno com média das notas igual ou maior que 7. Se houver chamada, também exige 75% de presença. A página fica em `/turmas/{id}/certificado`, com link no boletim.

O aluno vê só a própria entrega, a própria nota de prova e a própria presença. O docente vê a turma.

## Quando algo falha

Vida (`GET /actuator/health/liveness`) responde `200` mesmo com o Postgres fechado. Prontidão (`GET /actuator/health/readiness`) responde `503` e o tráfego deve ser recusado. As rotas de curso, turma, prática, prova, fórum, aviso, chamada e certificado respondem `503` com o Postgres fechado. Vida e prontidão não exigem sessão.

## Arranque

É preciso Java 21, Node.js 22, Docker e curl.

```bash
./scripts/start.sh
```

Na primeira execução o script cria um `.env` local, com senhas geradas, e sobe o Postgres, o acesso, a API e a interface. O `.env` não entra no repositório. O modelo vazio está em `.env.example`.

Se a API e a interface já respondem, o script avisa e não sobe outra cópia. Se a porta da API ou da interface está ocupada sem responder, ele para antes de misturar as duas instâncias.

A interface fica em `http://localhost:3000`. O acesso fica em `http://localhost:8180`. As senhas das contas `aluno`, `docente` e `administracao` ficam no `.env`.

## Versão

`VERSION` é a versão do produto. O CI publica a tag anotada `vX.Y.Z` quando essa versão chega em `main`.
