export function issuer(): string {
  return process.env.KEYCLOAK_ISSUER ?? "http://localhost:8180/realms/skillforge";
}

export function clientId(): string {
  return process.env.KEYCLOAK_CLIENT_ID ?? "skillforge";
}

export function webUrl(): string {
  return process.env.WEB_URL ?? "http://localhost:3000";
}

export function apiUrl(): string {
  return process.env.API_URL ?? "http://localhost:8080";
}

export function redirectUri(): string {
  return `${webUrl()}/callback`;
}
