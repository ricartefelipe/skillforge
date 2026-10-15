export type LessonSummary = {
  id: string;
  title: string;
  kind: "markdown" | "pdf" | "ebook" | "video";
  completed?: boolean;
};

export type Module = {
  id: string;
  name: string;
  lessons: LessonSummary[];
};

export type Course = {
  id: string;
  name: string;
  modules: Module[];
};

export type LessonDetail = {
  id: string;
  title: string;
  kind: "markdown" | "pdf" | "ebook" | "video";
  markdown?: string;
};

const KINDS = new Set(["markdown", "pdf", "ebook", "video"]);

export function narrowCourses(courses: Course[] | null, query: string): Course[] | null {
  if (courses === null) {
    return null;
  }
  const needle = query.trim().toLocaleLowerCase("pt-BR");
  if (needle.length === 0) {
    return courses;
  }
  return courses
    .map((course) => {
      if (course.name.toLocaleLowerCase("pt-BR").includes(needle)) {
        return course;
      }
      const modules = course.modules
        .map((module) => ({
          ...module,
          lessons: module.lessons.filter((lesson) => lesson.title.toLocaleLowerCase("pt-BR").includes(needle)),
        }))
        .filter(
          (module) =>
            module.name.toLocaleLowerCase("pt-BR").includes(needle) || module.lessons.length > 0,
        );
      return { ...course, modules };
    })
    .filter((course) => course.name.toLocaleLowerCase("pt-BR").includes(needle) || course.modules.length > 0);
}

export function canCompose(role: string): boolean {
  return role === "administracao";
}

export function catalogNotice(code: string | null): string | null {
  switch (code) {
    case "blank-course":
      return "O curso precisa de um nome.";
    case "blank-module":
      return "O módulo precisa de um nome.";
    case "blank-title":
      return "A aula precisa de um título.";
    case "blank-prompt":
      return "A mensagem precisa de um texto.";
    case "duplicate-course":
      return "Já existe um curso com esse nome.";
    case "markdown-only":
      return "A aula em markdown precisa do texto, e só dele.";
    case "file-only":
      return "O ebook ou o vídeo entra só como arquivo.";
    case "not-pdf":
      return "O arquivo não é um PDF.";
    case "not-ebook":
      return "O ebook precisa ser um PDF ou um EPUB.";
    case "not-video":
      return "O vídeo precisa ser MP4, MOV ou WebM.";
    case "too-large":
      return "O arquivo passa do limite: ebook até 100 MB, vídeo até 512 MB.";
    case "not-found":
      return "Não encontrei esse item.";
    case "unavailable":
      return "O catálogo não responde.";
    case null:
    case "":
      return null;
    default:
      return "Não foi possível guardar.";
  }
}

export function coursesFromBody(httpOk: boolean, body: unknown): Course[] | null {
  if (!httpOk || !Array.isArray(body)) {
    return null;
  }
  const courses: Course[] = [];
  for (const item of body) {
    const course = courseFrom(item);
    if (course === null) {
      return null;
    }
    courses.push(course);
  }
  return courses;
}

export function lessonFromBody(httpOk: boolean, body: unknown): LessonDetail | null {
  if (!httpOk || body === null || typeof body !== "object") {
    return null;
  }
  const id = text(body, "id");
  const title = text(body, "title");
  const kind = text(body, "kind");
  if (id === null || title === null || !isKind(kind)) {
    return null;
  }
  if (kind === "markdown") {
    const markdown = text(body, "markdown");
    if (markdown === null || markdown.trim().length === 0) {
      return null;
    }
    return { id, title, kind, markdown };
  }
  return { id, title, kind };
}

export function courseFrom(value: unknown): Course | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const id = text(value, "id");
  const name = text(value, "name");
  if (id === null || name === null || !("modules" in value) || !Array.isArray(value.modules)) {
    return null;
  }
  const modules: Module[] = [];
  for (const item of value.modules) {
    const parsed = moduleFrom(item);
    if (parsed === null) {
      return null;
    }
    modules.push(parsed);
  }
  return { id, name, modules };
}

function moduleFrom(value: unknown): Module | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const id = text(value, "id");
  const name = text(value, "name");
  if (id === null || name === null || !("lessons" in value) || !Array.isArray(value.lessons)) {
    return null;
  }
  const lessons: LessonSummary[] = [];
  for (const item of value.lessons) {
    const lesson = summaryFrom(item);
    if (lesson === null) {
      return null;
    }
    lessons.push(lesson);
  }
  return { id, name, lessons };
}

function summaryFrom(value: unknown): LessonSummary | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const id = text(value, "id");
  const title = text(value, "title");
  const kind = text(value, "kind");
  if (id === null || title === null || !isKind(kind)) {
    return null;
  }
  if (!("completed" in value)) {
    return { id, title, kind };
  }
  const completed = (value as Record<string, unknown>).completed;
  if (typeof completed !== "boolean") {
    return null;
  }
  return { id, title, kind, completed };
}

function text(value: object, key: string): string | null {
  const found = (value as Record<string, unknown>)[key];
  if (typeof found !== "string" || found.trim().length === 0) {
    return null;
  }
  return found;
}

function isKind(value: string | null): value is LessonSummary["kind"] {
  return value !== null && KINDS.has(value);
}
