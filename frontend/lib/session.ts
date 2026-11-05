import "server-only";
import { cookies } from "next/headers";

export const SESSION_COOKIE = "vc_session";

export async function setSession(token: string, expiresAt: string) {
  const store = await cookies();
  store.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    path: "/",
    expires: new Date(expiresAt),
  });
}

export async function getToken(): Promise<string | undefined> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value;
}

export async function clearSession() {
  const store = await cookies();
  store.delete(SESSION_COOKIE);
}
