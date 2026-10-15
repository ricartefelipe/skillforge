# Skillforge

Construção de competências pela prática.

O repositório, o pacote Java e a interface usam o mesmo nome: **skillforge**.

O ambiente hoje é a soma dos incrementos abaixo. Cada seção guarda o que entrou naquela etapa. O que veio depois amplia, e o registro permanece.

## Quem entra

O acesso é pelo Keycloak, em `http://localhost:8180`. Há três contas: `aluno`, `docente` e `administracao`. Cada uma tem só o primeiro nome. O arranque ajusta o perfil para não exigir e-mail nem sobrenome. As senhas ficam no `.env` local, geradas no arranque.

- Sem sessão, `GET /me` responde `401`.
- Com um papel institucional, `GET /me` devolve o nome e o papel.
- Zero papéis, ou mais de um entre aluno, docente e administração, responde `403`.
- Vida e prontidão continuam sem sessão. A prontidão também recusa tráfego se o acesso não responde.

A interface mostra Entrar, ou o nome e o papel com Sair. Com sessão, o menu tem Cursos, Aulas, Turmas, Boletim, Agenda e Recados. Docente e administração também têm a bancada Docente.

| Papel | O que faz |
| --- | --- |
| Aluno | Vê a grade das turmas em que está, conclui aula, entrega prática, faz a prova nas tentativas publicadas, avalia o colega do grupo, abre dúvida e lê o boletim, os recados e o certificado. |
| Docente | Ministra as próprias turmas. Publica módulo, material, videoaula, prática, prova, aviso e chamada. Devolve a nota. |
| Administração | Cria curso, abre turma, indica o docente e matricula. Vê o catálogo e as turmas inteiros. Publica o mesmo que o docente. |

## A turma, como está

A grade é o curso dividido em unidades. Cada unidade reúne os materiais, nesta ordem em que forem publicados: página, ebook e vídeo. O ebook é PDF ou EPUB, até 100 MB. O vídeo é MP4, MOV ou WebM, até 512 MB. A administração cria o curso. Quem ministra abre a unidade e publica o material no curso da própria turma.

A administração abre a turma, indica a conta do docente e matricula pela conta. O aluno vê só as turmas em que está. O docente vê só as que ministra. A administração vê todas. Dentro da turma a classe fica em grade, atividades, avisos, pessoas e grupos. O material se publica na unidade da grade e pode receber outros arquivos. A prova e a prática ficam em atividades. A aula abre com a grade ao lado. A home continua na primeira aula ainda não concluída. A busca percorre aulas, práticas e provas. A agenda é o mês, com os prazos no dia. Os recados avisam prova, aviso e devolutiva, e também saem por e-mail quando o correio e o endereço da conta estão configurados. O boletim marca aprovado a partir da média 7. Com pesos diferentes de 1, a média separa práticas e provas. Quem ministra pode editar o que ainda não teve entrega, arrastar a ordem da grade, ajustar o texto das questões mesmo depois da prova, e retirar aula, prática, prova e aviso. A aula também aceita um pacote SCORM e uma ferramenta externa aberta com a conta de quem está nela.

- O aluno marca a aula como concluída. A marca permanece. Marcar de novo não muda a conta.
- A prática tem enunciado, prazo opcional e rubrica opcional. O aluno entrega um texto. O docente devolve um comentário e a nota de 0 a 10. Com rubrica, a nota é a soma dos critérios. Depois da devolutiva, a entrega não muda.
- A prova tem prazo opcional, tempo opcional e de 1 a 5 tentativas. O padrão continua uma tentativa. Cada questão tem ao menos duas alternativas e uma só correta. O gabarito não sai para o aluno. A nota é 0 a 10, na proporção das questões certas, e o boletim fica com a melhor. O tempo esgotado grava 0. As questões podem vir do banco do curso.
- Depois do prazo, a entrega da prática ou da prova é recusada.
- Na aula, quem está no curso abre uma dúvida e os outros respondem. Quem está fora recebe `404`.
- O docente publica o aviso da turma e registra a chamada do dia. O mesmo dia não se repete.
- O certificado sai para o aluno com média das notas igual ou maior que 7. Se houver chamada, também exige 75% de presença. A página fica em `/turmas/{id}/certificado`, com link no boletim.

O aluno vê só a própria entrega, a própria nota de prova e a própria presença. O docente vê a turma.

| Rotina | Caminho |
| --- | --- |
| Cursos | `GET /courses`, `GET /courses/curriculum`, `GET /courses/teaching` |
| Curso, módulo, aula | `POST /courses`, `POST /courses/{id}/modules`, `POST /modules/{id}/lessons` |
| Aula e arquivo | `GET /lessons/{id}`, `GET /lessons/{id}/media`, `GET /lessons/{id}/assets` |
| Continuar | `GET /resume` |
| Recados | `GET /inbox` |
| Banco, grupos e revisão | `GET /courses/{id}/bank`, `GET /cohorts/{id}/groups`, `POST /cohorts/{id}/practices/{practiceId}/reviews` |
| Turmas e matrícula | `GET /cohorts`, `GET /cohorts/{id}`, `POST /cohorts`, `POST /cohorts/{id}/enrollments` |
| Conclusão da aula | `POST /cohorts/{id}/lessons/{lessonId}/completion` |
| Prática | `POST /cohorts/{id}/practices`, `POST .../submission`, `POST .../feedback` |
| Prova | `POST /cohorts/{id}/exams`, `POST /cohorts/{id}/exams/{examId}/attempt` |
| Aviso | `GET` e `POST /cohorts/{id}/announcements` |
| Chamada | `GET` e `POST /cohorts/{id}/attendance` |
| Certificado | `GET /cohorts/{id}/certificate` |
| Boletim | `GET /grades` |
| Fórum da aula | `GET` e `POST /lessons/{id}/doubts`, `POST /lessons/{id}/doubts/{doubtId}/replies` |

## Quando algo falha

Vida (`GET /actuator/health/liveness`) responde `200` mesmo com o Postgres fechado. Prontidão (`GET /actuator/health/readiness`) responde `503` e o tráfego deve ser recusado. As rotas de curso, turma, prática, prova, fórum, aviso, chamada e certificado respondem `503` com o Postgres fechado. Vida e prontidão não exigem sessão.

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
- Aluno e docente leem. Nesta etapa, só a administração cria o curso. A partir do incremento 6, quem ministra também publica módulo, material e videoaula no curso da própria turma.
- Nome em branco, curso repetido, arquivo que não é o tipo declarado, ou conteúdo acima do limite, são recusados.
- Com o Postgres fechado, o catálogo responde `503` e a vida continua `200`.

A interface lista o catálogo para quem entrou. A administração vê os formulários de todo curso. O docente vê os formulários do curso que ministra.

## Incremento 3

A administração abre uma turma para um curso, indica a conta do docente e matricula alunos pela conta.

- Sem sessão, `GET /cohorts` responde `401`.
- O aluno vê somente as turmas em que está. O docente vê somente as que ministra. A administração vê todas.
- A aula do curso aparece para quem está na turma. A conclusão da aula entrou no incremento 4.
- Nome em branco, docente em branco, aluno em branco, turma repetida no mesmo curso ou matrícula repetida são recusados.
- Com o Postgres fechado, a turma responde `503` e a vida continua `200`.

A interface lista as turmas de quem entrou. Só a administração abre turma e matricula.

## Incremento 4

O aluno marca a aula da turma como concluída. A marca permanece depois de sair e entrar.

- Só o aluno matriculado conclui a aula da própria turma.
- A turma mostra, para ele, quantas aulas concluiu. O docente vê a mesma conta de cada aluno.
- Marcar de novo não muda a conta. A conclusão da aula continua sem nota. A nota entrou na prática, no incremento 6, e na prova, no incremento 7.
- Com o Postgres fechado, a conclusão responde `503` e a vida continua `200`.

## Incremento 5

A turma tem práticas. O docente abre o enunciado, o aluno entrega um texto e o docente devolve um comentário. A nota dessa devolutiva entrou no incremento 6. Prazo e rubrica entraram no incremento 8.

- O aluno não abre prática. Quem não ministra a turma não a vê.
- Título repetido na mesma turma, texto em branco ou acima do limite são recusados.
- Depois da devolutiva, a entrega não muda.
- O aluno vê só a própria entrega. O docente vê as entregas da turma.
- Com o Postgres fechado, a prática responde `503` e a vida continua `200`.

## Incremento 6

O ambiente cobre a rotina de um AVA: cursos disponíveis, a grade cursada, a bancada do docente, nota, boletim e o fórum da aula.

- Quem ministra publica módulo, material e videoaula no curso da própria turma. Quem não ministra continua sem esse poder.
- Aulas lista só a grade de quem está na turma ou a ministra. A administração vê o catálogo inteiro.
- A devolutiva da prática traz nota de 0 a 10. O boletim mostra a média.
- Na aula, quem está no curso abre uma dúvida e os outros respondem. Quem está fora recebe `404`.
- Com o Postgres fechado, essas rotas respondem `503` e a vida continua `200`.

## Incremento 7

A área do docente publica a prova da turma. O gabarito não sai para o aluno. A tentativa é única e a nota entra no boletim.

- Só quem ministra, ou a administração, abre a prova.
- Cada questão tem ao menos duas alternativas e uma só correta.
- O aluno de fora da turma não vê a prova.
- A nota é 0 a 10, na proporção das questões certas.

## Incremento 8

Prazo, rubrica, chamada, aviso e certificado seguem a regra de uma turma a distância.

- Prática e prova podem fechar numa data. Depois do prazo, o aluno não entrega.
- A rubrica da prática soma os critérios e essa soma é a nota.
- O docente registra a chamada do dia. Com chamada aberta, o certificado exige 75% de presença.
- O aviso da turma aparece para quem está nela. A bancada do docente também publica o aviso. A chamada fica na página da turma, onde está a lista de alunos.
- O certificado sai com média igual ou maior que 7. Sem nenhuma chamada, a presença não bloqueia o certificado.

## Incremento 9

A sala de estudo fecha o que ainda faltava para a rotina da turma.

- A home continua na primeira aula da grade que o aluno ainda não concluiu.
- Quem ministra edita aula, prática sem entrega, aviso, prazo, tempo e número de tentativas da prova. Depois da primeira entrega ou prova, o conteúdo fica travado.
- O banco guarda questões do curso e a prova pode nascer delas.
- A prova pode ter tempo e mais de uma tentativa. Sem tempo, a entrega segue direta. Com tempo, o aluno começa e o relógio corre no servidor.
- A turma tem grupos. Quem está no mesmo grupo avalia a entrega do colega uma vez, com comentário e nota. A nota oficial continua a do docente.
- A aula aceita arquivos além do material principal.
- Recados entram quando sai aviso, prova ou devolutiva.
- Os pesos da média vão de 1 a 5. Iguais a 1, a média histórica permanece. Diferentes, práticas e provas pesam em separado.

## Incremento 10

A busca, o mês, a ordem da grade, o pacote e a ferramenta externa fecham o que ainda estava de fora.

- `GET /search?q=` procura aula, prática e prova no que a conta pode ver.
- A agenda desenha o mês. Cada dia com prazo abre a turma.
- Quem ministra arrasta as aulas entre as unidades. A posição fica gravada.
- O enunciado e o texto das alternativas podem mudar depois que alguém já fez a prova. A nota já dada permanece.
- Cada conta guarda o próprio e-mail em Recados. Se `MAIL_HOST` estiver definido, o recado também é enviado. Sem correio, o recado continua na interface.
- O material pode ser um zip SCORM, com `imsmanifest.xml`. A conclusão do pacote marca a aula.
- A ferramenta externa abre por um lançamento assinado. O segredo não sai para o navegador.

## Incremento 11

A visão noturna e o fechamento do que ainda estava pela metade.

- O sol liga a visão clara. A lua liga a visão noturna. A escolha fica no navegador.
- A busca também olha o texto da aula, o enunciado da prática e o enunciado da questão.
- Depois da prova, quem ministra inclui questão nova e retira questão. A nota já dada permanece. A questão retirada sai da prova seguinte.
- O pacote SCORM segue a ordem dos itens do manifesto. O próximo item abre quando o anterior foi concluído. O status do SCORM 2004 também conta.
- A ferramenta pode usar o login moderno: a plataforma publica a chave em `/lti/jwks` e devolve um token assinado.
- O fórum saiu do pacote do catálogo.
- Sem servidor de correio, a mensagem enviada aparece em Recados.

## Incremento 12

A gravação da turma deixou de ficar numa classe só.

- A turma guarda abertura, matrícula, conclusão e boletim.
- A prática, a prova e a sala têm cada uma o próprio adaptador.
- O repositório da turma não reúne mais esses três contratos.

## Incremento 13

A visão noturna deixa de divergir da página que o servidor já entregou.

- O tema gravado no navegador entra antes da pintura.
- Com a lua ligada, a página hidrata sem o aviso de atributos diferentes.

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
