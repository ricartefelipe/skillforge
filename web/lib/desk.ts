export type DeskLink = {
  href: string;
  label: string;
};

export type Desk = {
  kicker: string;
  title: string;
  lede: string;
  nav: DeskLink[];
  shortcuts: DeskLink[];
};

const agenda: DeskLink = { href: "/agenda", label: "Agenda" };
const notices: DeskLink = { href: "/recados", label: "Recados" };
const courses: DeskLink = { href: "/catalogo", label: "Meus cursos" };
const classes: DeskLink = { href: "/turmas", label: "Minhas turmas" };
const marking: DeskLink = { href: "/docente", label: "Correções" };

function withCommon(nav: DeskLink[], rest: Omit<Desk, "nav">): Desk {
  return { ...rest, nav: [...nav, agenda, notices] };
}

export function deskOf(role: string): Desk {
  switch (role) {
    case "aluno":
      return withCommon([courses, classes, { href: "/boletim", label: "Boletim" }], {
        kicker: "Aluno",
        title: "Continuar aprendendo",
        lede: "A aula em que você parou, as turmas e o boletim.",
        shortcuts: [courses, classes, { href: "/boletim", label: "Boletim" }],
      });
    case "docente":
      return withCommon([marking, classes, courses], {
        kicker: "Docente",
        title: "Suas turmas",
        lede: "Corrija as entregas, publique as aulas e faça a chamada.",
        shortcuts: [marking, classes, { href: "/catalogo", label: "Publicar aulas" }],
      });
    case "administracao":
      return withCommon(
        [
          { href: "/turmas", label: "Turmas" },
          { href: "/catalogo", label: "Cursos" },
          marking,
        ],
        {
          kicker: "Administração",
          title: "Administrar a escola",
          lede: "Abra a turma, indique o docente e matricule o aluno. O material fica em Cursos.",
          shortcuts: [
            { href: "/turmas", label: "Abrir turma" },
            { href: "/catalogo", label: "Criar curso" },
            marking,
          ],
        },
      );
    default:
      return {
        kicker: "Escola",
        title: "Skillforge",
        lede: "Entre com a conta do seu papel.",
        nav: [],
        shortcuts: [],
      };
  }
}
