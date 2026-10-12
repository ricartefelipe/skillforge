import { cookies } from "next/headers";

import { loadSession } from "../lib/access";
import { ACCESS_COOKIE } from "../lib/cookies";
import { apiUrl } from "../lib/openid";
import { SkillforgeHome } from "./home";

export default async function Page() {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const session = access ? await loadSession(access, apiUrl()) : null;
  return <SkillforgeHome session={session} />;
}
