export type Session = {
  name: string;
  role: string;
  label: string;
};

export function roleLabel(role: string): string | null {
  switch (role) {
    case "aluno":
      return "Aluno";
    case "docente":
      return "Docente";
    case "administracao":
      return "Administração";
    default:
      return null;
  }
}

export function sessionFromMe(httpOk: boolean, body: unknown): Session | null {
  if (!httpOk || body === null || typeof body !== "object") {
    return null;
  }

  const name = "name" in body && typeof body.name === "string" ? body.name.trim() : "";
  const role = "role" in body && typeof body.role === "string" ? body.role : "";
  const label = roleLabel(role);
  if (name.length === 0 || label === null) {
    return null;
  }

  return { name, role, label };
}

export async function loadSession(accessToken: string, apiUrl: string): Promise<Session | null> {
  try {
    const response = await fetch(`${apiUrl}/me`, {
      headers: { authorization: `Bearer ${accessToken}` },
      cache: "no-store",
    });
    const body: unknown = await response.json();
    return sessionFromMe(response.ok, body);
  } catch {
    return null;
  }
}
