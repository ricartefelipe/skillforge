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

export function deskOf(role: string): Desk {
  switch (role) {
    case "aluno":
      return {
        kicker: "Aluno",
        title: "Continuar aprendendo",
        lede: "A aula em que você parou, as turmas e o boletim.",
        nav: [
          { href: "/catalogo", label: "Meus cursos" },
          { href: "/turmas", label: "Minhas turmas" },
          { href: "/boletim", label: "Boletim" },
          { href: "/agenda", label: "Agenda" },
          { href: "/recados", label: "Recados" },
        ],
        shortcuts: [
          { href: "/catalogo", label: "Meus cursos" },
          { href: "/turmas", label: "Minhas turmas" },
          { href: "/boletim", label: "Boletim" },
        ],
      };
    case "docente":
      return {
        kicker: "Docente",
        title: "Suas turmas",
        lede: "Corrija as entregas, publique as aulas e faça a chamada.",
        nav: [
          { href: "/docente", label: "Correções" },
          { href: "/turmas", label: "Minhas turmas" },
          { href: "/catalogo", label: "Meus cursos" },
          { href: "/agenda", label: "Agenda" },
          { href: "/recados", label: "Recados" },
        ],
        shortcuts: [
          { href: "/docente", label: "Correções" },
          { href: "/turmas", label: "Minhas turmas" },
          { href: "/catalogo", label: "Publicar aulas" },
        ],
      };
    case "administracao":
      return {
        kicker: "Administração",
        title: "Administrar a escola",
        lede: "Abra a turma, indique o docente e matricule o aluno. O material fica em Cursos.",
        nav: [
          { href: "/turmas", label: "Turmas" },
          { href: "/catalogo", label: "Cursos" },
          { href: "/docente", label: "Correções" },
          { href: "/agenda", label: "Agenda" },
          { href: "/recados", label: "Recados" },
        ],
        shortcuts: [
          { href: "/turmas", label: "Abrir turma" },
          { href: "/catalogo", label: "Criar curso" },
          { href: "/docente", label: "Correções" },
        ],
      };
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
