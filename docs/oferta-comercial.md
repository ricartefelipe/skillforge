# Skillforge — plataforma de ensino, avaliação e certificados

Construção de competências pela prática. Plataforma de ensino a distância instalada por instituição ou
hospedada com isolamento comprovado.

## Para quem é e qual problema resolve

Escolas, cursos livres e áreas de treinamento que precisam **publicar conteúdo, avaliar e emitir
certificados** com papéis claros (aluno, docente, administração), sem montar um LMS do zero nem depender
de planilhas e provas soltas. O foco é a jornada completa: curso → matrícula → conteúdo → avaliação → nota
→ certificado, com as regras de nota e prazo no servidor.

## O que está incluído

- **Catálogo e aulas**: curso em unidades; materiais em ordem (página, ebook PDF/EPUB até 100 MB, vídeo
  MP4/MOV/WebM até 512 MB), pacote SCORM e ferramenta externa (LTI). Busca em aulas, práticas e provas.
- **Turmas e papéis**: administração cria curso e turma e matricula; docente vê e edita só as suas turmas;
  aluno vê só as turmas em que está. Progresso, presença, avisos, grupos de estudo e fórum por aula.
- **Provas e práticas**: prazo e tempo no servidor, 1 a 5 tentativas, banco de questões, correção
  determinística com pesos, revisão de nota versionada, avaliação entre pares. O gabarito nunca vai ao aluno.
- **Certificados**: critério de conclusão (média e presença), emissão por turma e boletim coerente com as
  regras do servidor.
- **Operação**: e-mail/recados, probes de liveness/readiness, Docker Compose, Keycloak para identidade.

## O que não está incluído (hoje)

- Marketplace de cursos, cobrança ao aluno embutida, proctoring/biometria, IA corrigindo avaliações
  sensíveis sem rubrica. Billing da plataforma é opcional conforme o modelo de licença.

## Modalidade e licença — a decidir com o cliente

- Instalação por instituição (origem dedicada) ou hospedagem com isolamento comprovado. Licença, suporte e
  SLA a definir. Sem preço, certificação, cliente ou receita presumidos aqui.

## Custos e dependências

- Infraestrutura do cliente (Postgres, Keycloak, armazenamento de mídia) e e-mail (SMTP) para notificações.
  Integrações institucionais (LTI/SCORM, SSO, importação de matrículas) exigem credenciais de sandbox do
  cliente para homologação.

## Onboarding e offboarding

- Onboarding: provisionar Compose/infra, carregar realm do Keycloak, criar administração, importar cursos e
  matrículas. Offboarding: exportação dos dados educacionais do cliente e descarte conforme política de
  retenção acordada.

## Demonstração, suporte e piloto

- Demo sintética com dados fictícios (sem dados reais). Piloto por turma, com acompanhamento de uma jornada
  completa até o certificado. Suporte conforme contrato.

## Privacidade

- Dados educacionais (notas, histórico) são sensíveis: retenção, backup/restore e minimização na verificação
  pública de certificado seguem a política e a legislação aplicáveis ao cliente.
